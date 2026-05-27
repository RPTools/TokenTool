use std::collections::HashSet;
use std::path::Path;
use lopdf::{Document, Object, Dictionary, Stream};
use base64::prelude::*;
use image::ImageEncoder;

/// Extracts embedded images and graphics from a specific page of a PDF document.
///
/// # Arguments
/// * `pdf_path` - The absolute filesystem path to the PDF document.
/// * `page_number` - The 1-based page index to extract from.
///
/// # Returns
/// * `Result<(Vec<String>, usize, usize), String>` - On success, returns a tuple containing:
///   1. A vector of base64-encoded PNG image data URLs.
///   2. The total page count of the document.
///   3. The total image count of all objects in the PDF document.
///   Or an error string.
pub fn extract_images_from_pdf_page<P: AsRef<Path>>(
    pdf_path: P,
    page_number: usize,
) -> Result<(Vec<String>, usize, usize), String> {
    let pdf_path_ref = pdf_path.as_ref();
    log::info!("Rust reading file bytes for path: {:?}", pdf_path_ref);

    // Read the file bytes ourselves using standard Rust filesystem API
    let file_bytes = std::fs::read(pdf_path_ref)
        .map_err(|e| format!("IO Error reading PDF file '{:?}': {}", pdf_path_ref, e))?;
    
    log::info!("Read PDF file bytes successfully. File size: {} bytes", file_bytes.len());

    // Parse the PDF from memory to bypass any file handle encoding/locking quirks in lopdf
    let mut doc = Document::load_mem(&file_bytes)
        .map_err(|e| format!("PDF parsing error: {}", e))?;

    log::info!("PDF parse successful. Total objects parsed: {}", doc.objects.len());
    log::info!("PDF encrypted: {}", doc.is_encrypted());

    // Attempt to decrypt with empty/default password if the PDF is encrypted
    if doc.is_encrypted() {
        match doc.decrypt(b"") {
            Ok(_) => log::info!("PDF successfully decrypted with default/empty password."),
            Err(e) => log::warn!("PDF decryption failed: {:?}", e),
        }
    }

    let mut pages = doc.get_pages();
    log::info!("lopdf doc.get_pages() returned {} pages.", pages.len());
    
    // Fallback: If pages map is empty, perform a flat scan of all PDF objects
    if pages.is_empty() {
        log::info!("Performing flat scan of all PDF objects to locate candidate Page dictionaries...");
        let mut page_ids = Vec::new();
        for (id, object) in &doc.objects {
            if let Ok(dict) = object.as_dict() {
                let has_type_page = dict.get(b"Type").and_then(|o| o.as_name()).map_or(false, |t| t == b"Page");
                let has_mediabox = dict.get(b"MediaBox").is_ok();
                let has_parent = dict.get(b"Parent").is_ok();
                let has_kids = dict.get(b"Kids").is_ok();
                
                // Pages tree nodes have Kids and Parent/Type Pages, individual pages have Parent & MediaBox
                if has_type_page || (has_mediabox && has_parent && !has_kids) {
                    page_ids.push(*id);
                    log::info!("Found candidate page object {:?}: has_type_page={}, has_mediabox={}, has_parent={}", 
                             id, has_type_page, has_mediabox, has_parent);
                }
            }
        }
        
        // Populate the pages map
        for (i, page_id) in page_ids.into_iter().enumerate() {
            pages.insert((i + 1) as u32, page_id);
        }
        log::info!("Flat scan found {} candidate page objects.", pages.len());
    }

    let total_pages = pages.len();
    
    let page_id = pages.get(&(page_number as u32))
        .ok_or_else(|| format!("Page {} not found in PDF (Total pages: {})", page_number, total_pages))?;

    let page_obj = doc.get_object(*page_id).map_err(|e| e.to_string())?;
    let page_dict = page_obj.as_dict().map_err(|_| "Page is not a dictionary".to_string())?;

    let mut extracted_images = Vec::new();
    let mut processed_streams: HashSet<lopdf::ObjectId> = HashSet::new();

    // 1. Extract from Page Resources (resolving inherited resources from parent nodes in Pages tree)
    if let Some(res_dict) = find_resources(&doc, page_dict) {
        extract_from_resources(&doc, res_dict, &mut extracted_images, &mut processed_streams);
    }

    // 2. Extract from Page Annotations (Forms/Buttons appearances, e.g. Paizo maps)
    if let Ok(annots) = page_dict.get(b"Annots") {
        if let Ok(annots_arr) = annots.as_array() {
            for annot_ref in annots_arr {
                if let Ok(annot_id) = annot_ref.as_reference() {
                    if let Ok(annot_obj) = doc.get_object(annot_id) {
                        if let Ok(annot_dict) = annot_obj.as_dict() {
                            extract_from_annotation(&doc, annot_dict, &mut extracted_images, &mut processed_streams);
                        }
                    }
                }
            }
        }
    }

    let total_images = doc.objects.iter().filter(|(_, object)| {
        if let Ok(dict) = object.as_dict() {
            dict.get(b"Subtype").and_then(|o| o.as_name()).map_or(false, |s| s == b"Image")
        } else if let Ok(stream) = object.as_stream() {
            stream.dict.get(b"Subtype").and_then(|o| o.as_name()).map_or(false, |s| s == b"Image")
        } else {
            false
        }
    }).count();

    Ok((extracted_images, total_pages, total_images))
}

/// Resolves a PDF Object that may be either an inline Dictionary or an indirect Reference to
/// a Dictionary. Returns a borrowed reference to the Dictionary, or None if resolution fails.
/// This eliminates M-15: the same two-branch resolution pattern was copy-pasted 3+ times.
fn resolve_to_dict<'a>(doc: &'a Document, obj: &'a Object) -> Option<&'a Dictionary> {
    if let Ok(ref_id) = obj.as_reference() {
        doc.get_object(ref_id).ok()?.as_dict().ok()
    } else {
        obj.as_dict().ok()
    }
}

/// Resolves the Resources key in a dictionary, which may itself be an indirect Reference or an
/// inline Dictionary. Returns None if the key is absent or cannot be resolved.
fn resolve_resources<'a>(doc: &'a Document, dict: &'a Dictionary) -> Option<&'a Dictionary> {
    let resources = dict.get(b"Resources").ok()?;
    resolve_to_dict(doc, resources)
}

/// Locates the Resources dictionary for a PDF page, walking up the Pages tree
/// if the page dictionary does not contain an inline Resources entry.
///
/// PDF pages can inherit Resources from ancestor nodes in the page tree (per
/// the PDF spec §7.7.3.4). This function checks the page itself first, then
/// traverses the `Parent` chain until a Resources dictionary is found.
fn find_resources<'a>(doc: &'a Document, page_dict: &'a Dictionary) -> Option<&'a Dictionary> {
    if let Some(res_dict) = resolve_resources(doc, page_dict) {
        return Some(res_dict);
    }

    // Traverse parent Pages chain to look for inherited Resources dictionary
    let mut current_dict = page_dict;
    while let Ok(parent_ref) = current_dict.get(b"Parent").and_then(|p| p.as_reference()) {
        if let Ok(parent_obj) = doc.get_object(parent_ref) {
            if let Ok(parent_dict) = parent_obj.as_dict() {
                if let Some(res_dict) = resolve_resources(doc, parent_dict) {
                    return Some(res_dict);
                }
                current_dict = parent_dict;
            } else {
                break;
            }
        } else {
            break;
        }
    }

    None
}

/// Iterates XObject entries within a Resources dictionary, extracting Image
/// streams as base64-encoded PNGs and recursively descending into Form XObjects.
///
/// Tracks already-processed object IDs via `processed` to avoid duplicating
/// images that are referenced by multiple resource dictionaries.
fn extract_from_resources(
    doc: &Document,
    resources: &Dictionary,
    extracted: &mut Vec<String>,
    processed: &mut HashSet<lopdf::ObjectId>,
) {
    if let Ok(xobjects) = resources.get(b"XObject") {
        if let Some(dict) = resolve_to_dict(doc, xobjects) {
            for (_, val) in dict.iter() {
                if let Ok(ref_id) = val.as_reference() {
                    if processed.contains(&ref_id) {
                        continue;
                    }
                    processed.insert(ref_id);

                    if let Ok(obj) = doc.get_object(ref_id) {
                        if let Ok(stream) = obj.as_stream() {
                            let subtype = stream.dict.get(b"Subtype")
                                .ok()
                                .and_then(|o| o.as_name().ok());

                            if subtype == Some(b"Image") {
                                if let Some(base64_img) = process_image_stream(stream) {
                                    extracted.push(base64_img);
                                }
                            } else if subtype == Some(b"Form") {
                                // Recursive crawl: Form objects can have their own nested resources
                                if let Some(form_res_dict) = find_resources(doc, &stream.dict) {
                                    extract_from_resources(doc, form_res_dict, extracted, processed);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/// Extracts images from a page annotation's Appearance dictionary (`/AP`).
///
/// Annotations (form fields, buttons, stamps) can reference Form XObjects in
/// their Normal (`/N`), Rollover (`/R`), and Down (`/D`) appearance states.
/// Each appearance stream may contain its own nested Resources with images.
fn extract_from_annotation(
    doc: &Document,
    annot: &Dictionary,
    extracted: &mut Vec<String>,
    processed: &mut HashSet<lopdf::ObjectId>,
) {
    if let Ok(ap) = annot.get(b"AP") {
        let ap_dict = resolve_to_dict(doc, ap);

        if let Some(dict) = ap_dict {
            for state_key in &[b"N" as &[u8], b"R" as &[u8], b"D" as &[u8]] {
                if let Ok(state_obj) = dict.get(state_key) {
                    if let Ok(state_id) = state_obj.as_reference() {
                        if let Ok(stream_obj) = doc.get_object(state_id) {
                            if let Ok(stream) = stream_obj.as_stream() {
                                if let Some(form_res_dict) = find_resources(doc, &stream.dict) {
                                    extract_from_resources(doc, form_res_dict, extracted, processed);
                                }
                            }
                        }
                    } else if let Ok(state_dict) = state_obj.as_dict() {
                        for (_, val) in state_dict.iter() {
                            if let Ok(sub_id) = val.as_reference() {
                                if let Ok(stream_obj) = doc.get_object(sub_id) {
                                    if let Ok(stream) = stream_obj.as_stream() {
                                        if let Some(form_res_dict) = find_resources(doc, &stream.dict) {
                                            extract_from_resources(doc, form_res_dict, extracted, processed);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/// Implements the Paeth predictor function from the PNG specification (RFC 2083 §6.6).
///
/// Given three neighboring pixel bytes (`a` = left, `b` = above, `c` = upper-left),
/// returns the one closest to the linear predictor `p = a + b - c`. Used by
/// [`decode_png_predictor`] for filter type 4.
fn paeth_predictor(a: u8, b: u8, c: u8) -> u8 {
    let a_i = a as i16;
    let b_i = b as i16;
    let c_i = c as i16;

    let p = a_i + b_i - c_i;
    let pa = (p - a_i).abs();
    let pb = (p - b_i).abs();
    let pc = (p - c_i).abs();

    if pa <= pb && pa <= pc {
        a
    } else if pb <= pc {
        b
    } else {
        c
    }
}

/// Decodes raw image data that uses PNG-style row filters (predictor values 10–14).
///
/// PDF streams with `/Predictor` ≥ 10 in their `/DecodeParms` embed per-row filter
/// bytes identical to those in the PNG format. This function reverses the filtering
/// to recover the original pixel data.
///
/// # Arguments
/// * `columns` - Number of pixel columns (image width).
/// * `colors` - Number of color components per pixel (1 for gray, 3 for RGB, 4 for CMYK).
/// * `bits_per_component` - Bit depth per component (typically 8).
/// * `data` - The filtered byte stream, where each row is prefixed by a 1-byte filter type.
///
/// # Returns
/// `Some(Vec<u8>)` containing the unfiltered pixel data, or `None` if the input
/// length is invalid or a row slice is out of bounds.
fn decode_png_predictor(
    columns: usize,
    colors: usize,
    bits_per_component: usize,
    data: &[u8],
) -> Option<Vec<u8>> {
    let bytes_per_pixel = (colors * bits_per_component + 7) / 8;
    let row_len = (columns * colors * bits_per_component + 7) / 8;
    let predictor_row_len = row_len + 1;

    if data.is_empty() || data.len() % predictor_row_len != 0 {
        return None;
    }

    let rows = data.len() / predictor_row_len;
    let mut decompressed = vec![0u8; rows * row_len];

    for r in 0..rows {
        let row_start = r * predictor_row_len;
        let filter_type = data[row_start];
        let mut raw_row = vec![0u8; row_len];
        
        let src_end = row_start + predictor_row_len;
        if src_end <= data.len() {
            raw_row.copy_from_slice(&data[row_start + 1..src_end]);
        } else {
            return None;
        }

        let prev_row_start = if r > 0 { Some((r - 1) * row_len) } else { None };

        match filter_type {
            0 => { // None
                // raw_row is correct as is
            }
            1 => { // Sub
                for i in 0..row_len {
                    let left = if i >= bytes_per_pixel { raw_row[i - bytes_per_pixel] } else { 0 };
                    raw_row[i] = raw_row[i].wrapping_add(left);
                }
            }
            2 => { // Up
                if let Some(prev) = prev_row_start {
                    for i in 0..row_len {
                        let up = decompressed[prev + i];
                        raw_row[i] = raw_row[i].wrapping_add(up);
                    }
                }
            }
            3 => { // Average
                for i in 0..row_len {
                    let left = if i >= bytes_per_pixel { raw_row[i - bytes_per_pixel] } else { 0 };
                    let up = if let Some(prev) = prev_row_start { decompressed[prev + i] } else { 0 };
                    raw_row[i] = raw_row[i].wrapping_add(((left as u16 + up as u16) / 2) as u8);
                }
            }
            4 => { // Paeth
                for i in 0..row_len {
                    let left = if i >= bytes_per_pixel { raw_row[i - bytes_per_pixel] } else { 0 };
                    let up = if let Some(prev) = prev_row_start { decompressed[prev + i] } else { 0 };
                    let corner = if i >= bytes_per_pixel {
                        if let Some(prev) = prev_row_start { decompressed[prev + i - bytes_per_pixel] } else { 0 }
                    } else { 0 };

                    raw_row[i] = raw_row[i].wrapping_add(paeth_predictor(left, up, corner));
                }
            }
            _ => {
                // Unsupported filter type, use original row content
            }
        }

        let dest_start = r * row_len;
        decompressed[dest_start..dest_start + row_len].copy_from_slice(&raw_row);
    }

    Some(decompressed)
}

/// Decodes a single PDF image XObject stream into a base64-encoded PNG string.
///
/// Handles three major image encodings:
/// - **JPEG** (`DCTDecode`): passed through as raw bytes and base64-encoded directly.
/// - **Raw / Flate-decoded**: decompressed, PNG-predictor-decoded if needed, then
///   re-encoded as a PNG. Supports DeviceGray (including 1-bit), DeviceRGB, and
///   DeviceCMYK (converted to RGB).
///
/// Returns `None` if the stream cannot be decoded or the dimensions are invalid.
fn process_image_stream(stream: &Stream) -> Option<String> {
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
        if width_val <= 0 || height_val <= 0 || width_val > 16384 || height_val > 16384 {
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

            // Convert colors or upscale bit-depth to screen RGB/Luminance bytes
            let data_to_encode = if color_space_slice == Some(b"DeviceGray") && bits_per_component == 1 {
                let mut upscaled = Vec::with_capacity((width * height) as usize);
                let row_bytes = (width + 7) / 8;
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
                return Some(BASE64_STANDARD.encode(&png_bytes));
            }
        }
    }

    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_paeth_predictor() {
        assert_eq!(paeth_predictor(0, 0, 0), 0);
        assert_eq!(paeth_predictor(10, 20, 30), 10); // p=0, pa=10, pb=20, pc=30 -> a
        assert_eq!(paeth_predictor(50, 150, 100), 100); // p=100, pa=50, pb=50, pc=0 -> c
        assert_eq!(paeth_predictor(200, 10, 50), 200);
    }

    #[test]
    fn test_decode_png_predictor_none() {
        // filter=0, data=[10, 20, 30]
        // row_len = 3 (3 cols, 1 color, 8 bit)
        let data = vec![0, 10, 20, 30];
        let decoded = decode_png_predictor(3, 1, 8, &data).unwrap();
        assert_eq!(decoded, vec![10, 20, 30]);
    }

    #[test]
    fn test_decode_png_predictor_sub() {
        // filter=1, data=[10, 20, 30]
        // bytes_per_pixel = 1.
        // decoded should be: 10, 10+20=30, 30+30=60
        let data = vec![1, 10, 20, 30];
        let decoded = decode_png_predictor(3, 1, 8, &data).unwrap();
        assert_eq!(decoded, vec![10, 30, 60]);
    }

    #[test]
    fn test_decode_png_predictor_up() {
        // 2 rows, filter=2 (Up) for second row
        // Row 1: filter=0, [10, 20]
        // Row 2: filter=2, [5, 15]
        // decoded row 2: [10+5=15, 20+15=35]
        let data = vec![
            0, 10, 20,
            2, 5, 15
        ];
        let decoded = decode_png_predictor(2, 1, 8, &data).unwrap();
        assert_eq!(decoded, vec![10, 20, 15, 35]);
    }

    #[test]
    fn test_decode_png_predictor_invalid_length() {
        let data = vec![0, 10]; // Missing bytes for row
        assert_eq!(decode_png_predictor(2, 1, 8, &data), None);
    }

    #[test]
    #[ignore]
    fn test_parse_pdf() {
        let pdf_path = std::env::var("TEST_PDF_PATH")
            .unwrap_or_else(|_| "C:\\Users\\matta\\Downloads\\Wave-Echo-Cave.pdf".to_string());
        
        if !std::path::Path::new(&pdf_path).exists() {
            println!("Skipping test: File does not exist at '{}'. Set TEST_PDF_PATH environment variable to run this test with a real PDF.", pdf_path);
            return;
        }

        println!("Testing PDF extraction for: {}", pdf_path);
        
        if let Ok(file_bytes) = std::fs::read(&pdf_path) {
            let first_bytes = &file_bytes[..std::cmp::min(100, file_bytes.len())];
            println!("First 100 bytes of PDF: {:?}", String::from_utf8_lossy(first_bytes));
        }

        match extract_images_from_pdf_page(&pdf_path, 1) {
            Ok((images, pages, total_images)) => {
                println!("SUCCESS! Extracted {} images, total pages: {}, total images: {}", images.len(), pages, total_images);
            }
            Err(e) => {
                println!("FAILED: {}", e);
                panic!("PDF extraction failed: {}", e);
            }
        }
    }
}

