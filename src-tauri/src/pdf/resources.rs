use std::collections::HashSet;
use lopdf::{Document, Object, Dictionary};

use super::image::process_image_stream;

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
pub(crate) fn find_resources<'a>(doc: &'a Document, page_dict: &'a Dictionary) -> Option<&'a Dictionary> {
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
pub(crate) fn extract_from_resources(
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
pub(crate) fn extract_from_annotation(
    doc: &Document,
    annot: &Dictionary,
    extracted: &mut Vec<String>,
    processed: &mut HashSet<lopdf::ObjectId>,
) {
    let ap_dict = annot.get(b"AP")
        .ok()
        .and_then(|ap| resolve_to_dict(doc, ap));

    let dict = match ap_dict {
        Some(d) => d,
        None => return,
    };

    for state_key in &[b"N" as &[u8], b"R" as &[u8], b"D" as &[u8]] {
        let state_obj = match dict.get(state_key) {
            Ok(obj) => obj,
            Err(_) => continue,
        };

        if let Ok(state_id) = state_obj.as_reference() {
            process_stream_ref(doc, state_id, extracted, processed);
        } else if let Ok(state_dict) = state_obj.as_dict() {
            for (_, val) in state_dict.iter() {
                if let Ok(sub_id) = val.as_reference() {
                    process_stream_ref(doc, sub_id, extracted, processed);
                }
            }
        }
    }
}

#[inline]
fn process_stream_ref(
    doc: &Document,
    ref_id: lopdf::ObjectId,
    extracted: &mut Vec<String>,
    processed: &mut HashSet<lopdf::ObjectId>,
) {
    if let Ok(stream_obj) = doc.get_object(ref_id) {
        if let Ok(stream) = stream_obj.as_stream() {
            if let Some(form_res_dict) = find_resources(doc, &stream.dict) {
                extract_from_resources(doc, form_res_dict, extracted, processed);
            }
        }
    }
}
