use std::collections::HashSet;
use std::path::Path;
use lopdf::{Document, Object, Dictionary, Stream};
use base64::prelude::*;
use image::ImageEncoder;

pub fn extract_images_from_pdf_page<P: AsRef<Path>>(
    pdf_path: P,
    page_number: usize,
) -> Result<Vec<String>, String> {
    let doc = Document::load(pdf_path).map_err(|e| e.to_string())?;

    let pages = doc.get_pages();
    let page_id = pages.get(&(page_number as u32))
        .ok_or_else(|| format!("Page {} not found in PDF", page_number))?;

    let page_obj = doc.get_object(*page_id).map_err(|e| e.to_string())?;
    let page_dict = page_obj.as_dict().map_err(|_| "Page is not a dictionary".to_string())?;

    let mut extracted_images = Vec::new();
    let mut processed_streams: HashSet<lopdf::ObjectId> = HashSet::new();

    // 1. Extract from standard Page Resources
    if let Ok(resources) = page_dict.get(b"Resources") {
        if let Ok(res_id) = resources.as_reference() {
            if let Ok(res_obj) = doc.get_object(res_id) {
                if let Ok(res_dict) = res_obj.as_dict() {
                    extract_from_resources(&doc, res_dict, &mut extracted_images, &mut processed_streams);
                }
            }
        } else if let Ok(res_dict) = resources.as_dict() {
            extract_from_resources(&doc, res_dict, &mut extracted_images, &mut processed_streams);
        }
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

    Ok(extracted_images)
}

fn extract_from_resources(
    doc: &Document,
    resources: &Dictionary,
    extracted: &mut Vec<String>,
    processed: &mut HashSet<lopdf::ObjectId>,
) {
    if let Ok(xobjects) = resources.get(b"XObject") {
        // XObject can be a reference to a dictionary or an inline dictionary
        let xobj_dict: Option<&Dictionary> = if let Ok(xobj_id) = xobjects.as_reference() {
            doc.get_object(xobj_id).ok().and_then(|o| o.as_dict().ok())
        } else {
            xobjects.as_dict().ok()
        };

        if let Some(dict) = xobj_dict {
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
                                if let Ok(form_res) = stream.dict.get(b"Resources") {
                                    if let Ok(form_res_id) = form_res.as_reference() {
                                        if let Ok(form_res_obj) = doc.get_object(form_res_id) {
                                            if let Ok(form_res_dict) = form_res_obj.as_dict() {
                                                extract_from_resources(doc, form_res_dict, extracted, processed);
                                            }
                                        }
                                    } else if let Ok(form_res_dict) = form_res.as_dict() {
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

fn extract_from_annotation(
    doc: &Document,
    annot: &Dictionary,
    extracted: &mut Vec<String>,
    processed: &mut HashSet<lopdf::ObjectId>,
) {
    if let Ok(ap) = annot.get(b"AP") {
        let ap_dict: Option<&Dictionary> = if let Ok(ap_id) = ap.as_reference() {
            doc.get_object(ap_id).ok().and_then(|o| o.as_dict().ok())
        } else {
            ap.as_dict().ok()
        };

        if let Some(dict) = ap_dict {
            // Interactive buttons can map N (Normal), R (Rollover), or D (Down) appearances
            for state_key in &[b"N" as &[u8], b"R" as &[u8], b"D" as &[u8]] {
                if let Ok(state_obj) = dict.get(state_key) {
                    if let Ok(state_id) = state_obj.as_reference() {
                        if let Ok(stream_obj) = doc.get_object(state_id) {
                            if let Ok(stream) = stream_obj.as_stream() {
                                if let Ok(form_res) = stream.dict.get(b"Resources") {
                                    if let Ok(form_res_id) = form_res.as_reference() {
                                        if let Ok(form_res_obj) = doc.get_object(form_res_id) {
                                            if let Ok(form_res_dict) = form_res_obj.as_dict() {
                                                extract_from_resources(doc, form_res_dict, extracted, processed);
                                            }
                                        }
                                    } else if let Ok(form_res_dict) = form_res.as_dict() {
                                        extract_from_resources(doc, form_res_dict, extracted, processed);
                                    }
                                }
                            }
                        }
                    } else if let Ok(state_dict) = state_obj.as_dict() {
                        // AP states can map to sub-dictionaries (e.g. /On and /Off checkbox appearances)
                        for (_, val) in state_dict.iter() {
                            if let Ok(sub_id) = val.as_reference() {
                                if let Ok(stream_obj) = doc.get_object(sub_id) {
                                    if let Ok(stream) = stream_obj.as_stream() {
                                        if let Ok(form_res) = stream.dict.get(b"Resources") {
                                            if let Ok(form_res_id) = form_res.as_reference() {
                                                if let Ok(form_res_obj) = doc.get_object(form_res_id) {
                                                    if let Ok(form_res_dict) = form_res_obj.as_dict() {
                                                        extract_from_resources(doc, form_res_dict, extracted, processed);
                                                    }
                                                }
                                            } else if let Ok(form_res_dict) = form_res.as_dict() {
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
}

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
        // For JPEG, the raw stream content is already a valid JPEG file
        let data = &stream.content;
        if !data.is_empty() {
            return Some(BASE64_STANDARD.encode(data));
        }
    } else {
        // Lossless raster conversion (FlateDecode or raw decompressed pixel buffer)
        let width = stream.dict.get(b"Width").ok()?.as_i64().ok()? as u32;
        let height = stream.dict.get(b"Height").ok()?.as_i64().ok()? as u32;

        // Parse color space — can be a Name or an Array like [/Indexed, /DeviceRGB, ...]
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

        if let Ok(decompressed) = stream.decompressed_content() {
            // Determine color type and map raw bytes to standard PNG
            let color_type = match color_space_slice {
                Some(b"DeviceRGB") => image::ColorType::Rgb8,
                Some(b"DeviceGray") => image::ColorType::L8,
                Some(b"DeviceCMYK") => image::ColorType::Rgb8, // Convert CMYK below
                _ => image::ColorType::Rgb8
            };

            // Perform CMYK color-space transformation to screen RGB
            let data_to_encode = if color_space_slice == Some(b"DeviceCMYK") {
                let mut rgb = Vec::with_capacity((decompressed.len() / 4) * 3);
                for chunk in decompressed.chunks_exact(4) {
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
                decompressed
            };

            let expected_len = (width * height * color_type.bits_per_pixel() as u32 / 8) as usize;
            if data_to_encode.len() >= expected_len {
                let mut png_bytes = Vec::new();
                let encoder = image::codecs::png::PngEncoder::new(&mut png_bytes);
                if encoder.write_image(&data_to_encode[0..expected_len], width, height, color_type.into()).is_ok() {
                    return Some(BASE64_STANDARD.encode(&png_bytes));
                }
            }
        }
    }

    None
}
