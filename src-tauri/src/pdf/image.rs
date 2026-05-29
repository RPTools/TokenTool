use lopdf::{Object, Stream};
use base64::prelude::*;
use image::ImageEncoder;

use super::predictor::decode_png_predictor;

const MAX_IMAGE_DIMENSION: i64 = 16_384;

/// Decodes a single PDF image XObject stream into a base64-encoded PNG string.
///
/// Handles three major image encodings:
/// - **JPEG** (`DCTDecode`): passed through as raw bytes and base64-encoded directly.
/// - **Raw / Flate-decoded**: decompressed, PNG-predictor-decoded if needed, then
///   re-encoded as a PNG. Supports DeviceGray (including 1-bit), DeviceRGB, and
///   DeviceCMYK (converted to RGB).
///
/// Returns `None` if the stream cannot be decoded or the dimensions are invalid.
pub(crate) fn process_image_stream(stream: &Stream) -> Option<String> {
    let filter = stream.dict.get(b"Filter").ok();

    let is_jpeg = if let Some(filter_obj) = filter {
        match filter_obj {
            Object::Name(name) => name == b"DCTDecode",
            Object::Array(arr) => arr.iter().any(|o| o.as_name().ok() == Some(b"DCTDecode")),
            _ => false
        }
    } else {
        false
    };

    if is_jpeg {
        let data = &stream.content;
        if !data.is_empty() {
            return Some(BASE64_STANDARD.encode(data));
        }
    } else {
        let width_val = stream.dict.get(b"Width").ok()?.as_i64().ok()?;
        let height_val = stream.dict.get(b"Height").ok()?.as_i64().ok()?;
        
        // Validate image dimensions to prevent integer truncation, negative wrap-around, and DoS OOM
        if width_val <= 0 || height_val <= 0 || width_val > MAX_IMAGE_DIMENSION || height_val > MAX_IMAGE_DIMENSION {
            return None;
        }
        
        let width = width_val as u32;
        let height = height_val as u32;
        
        let bits_per_component_val = stream.dict.get(b"BitsPerComponent")
            .ok()
            .and_then(|o| o.as_i64().ok())
            .unwrap_or(8);
            
        if bits_per_component_val <= 0 || bits_per_component_val > 16 {
            return None;
        }
        let bits_per_component = bits_per_component_val as usize;

        let color_space_name: Option<Vec<u8>> = stream.dict.get(b"ColorSpace").ok().and_then(|cs| {
            if let Ok(name) = cs.as_name() {
                Some(name.to_vec())
            } else if let Ok(arr) = cs.as_array() {
                arr.first().and_then(|first| first.as_name().ok().map(|n| n.to_vec()))
            } else {
                None
            }
        });

        let color_space_slice = color_space_name.as_deref();

        // M-16: Consolidate color space matching into a single source of truth.
        // Previously, color_space_slice was matched twice separately to get `colors`
        // and then `color_type`. Now both are derived together.
        let (colors, color_type) = match color_space_slice {
            Some(b"DeviceGray") => (1usize, image::ColorType::L8),
            Some(b"DeviceCMYK") => (4usize, image::ColorType::Rgb8), // CMYK is converted to RGB on encode
            _ => (3usize, image::ColorType::Rgb8),                    // DeviceRGB and unknown default to RGB
        };

        // Parse PNG Predictor from DecodeParms
        let predictor = stream.dict.get(b"DecodeParms").ok().and_then(|dp| {
            if let Ok(dict) = dp.as_dict() {
                dict.get(b"Predictor").ok().and_then(|o| o.as_i64().ok())
            } else if let Ok(arr) = dp.as_array() {
                arr.first().and_then(|f| f.as_dict().ok().and_then(|d| d.get(b"Predictor").ok().and_then(|o| o.as_i64().ok())))
            } else {
                None
            }
        }).unwrap_or(1);

        if let Ok(decompressed) = stream.decompressed_content() {
            // Apply PNG predictor decoding if required
            let decoded_data = if predictor >= 10 {
                if let Some(decoded) = decode_png_predictor(width as usize, colors, bits_per_component, &decompressed) {
                    decoded
                } else {
                    decompressed
                }
            } else {
                decompressed
            };

            return encode_to_png(decoded_data, width, height, color_space_slice, bits_per_component, color_type);
        }
    }

    None
}

/// Helper function to handle color conversion and PNG encoding
fn encode_to_png(
    decoded_data: Vec<u8>,
    width: u32,
    height: u32,
    color_space_slice: Option<&[u8]>,
    bits_per_component: usize,
    color_type: image::ColorType,
) -> Option<String> {
    // Convert colors or upscale bit-depth to screen RGB/Luminance bytes
    let data_to_encode = if color_space_slice == Some(b"DeviceGray") && bits_per_component == 1 {
        let mut upscaled = Vec::with_capacity((width * height) as usize);
        let row_bytes = width.div_ceil(8);
        for r in 0..height {
            let row_start = (r * row_bytes) as usize;
            for c in 0..width {
                let byte_idx = row_start + (c as usize / 8);
                if byte_idx < decoded_data.len() {
                    let byte = decoded_data[byte_idx];
                    let bit_shift = 7 - (c % 8);
                    let val = if (byte >> bit_shift) & 1 == 1 { 255 } else { 0 };
                    upscaled.push(val);
                } else {
                    upscaled.push(0);
                }
            }
        }
        upscaled
    } else if color_space_slice == Some(b"DeviceCMYK") {
        let mut rgb = Vec::with_capacity((decoded_data.len() / 4) * 3);
        for chunk in decoded_data.chunks_exact(4) {
            let c = chunk[0] as f32 / 255.0;
            let m = chunk[1] as f32 / 255.0;
            let y = chunk[2] as f32 / 255.0;
            let k = chunk[3] as f32 / 255.0;

            let r = (255.0 * (1.0 - c) * (1.0 - k)) as u8;
            let g = (255.0 * (1.0 - m) * (1.0 - k)) as u8;
            let b = (255.0 * (1.0 - y) * (1.0 - k)) as u8;

            rgb.push(r);
            rgb.push(g);
            rgb.push(b);
        }
        rgb
    } else {
        decoded_data
    };

    let expected_len = (width * height * color_type.bits_per_pixel() as u32 / 8) as usize;
    let mut final_data = data_to_encode;
    if final_data.len() < expected_len {
        final_data.resize(expected_len, 0);
    }

    let mut png_bytes = Vec::new();
    let encoder = image::codecs::png::PngEncoder::new(&mut png_bytes);
    if encoder.write_image(&final_data[0..expected_len], width, height, color_type.into()).is_ok() {
        Some(BASE64_STANDARD.encode(&png_bytes))
    } else {
        None
    }
}
