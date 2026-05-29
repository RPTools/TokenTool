mod predictor;
mod image;
mod resources;

use std::collections::HashSet;
use std::path::Path;
use lopdf::Document;
use thiserror::Error;

use resources::{find_resources, extract_from_resources, extract_from_annotation};

#[derive(Error, Debug)]
pub enum PdfError {
    #[error("IO error reading PDF file '{path}': {source}")]
    Io {
        path: String,
        #[source]
        source: std::io::Error,
    },
    #[error("PDF parsing error: {0}")]
    Parse(String),
    #[error("PDF decryption failed: {0}")]
    DecryptionFailed(String),
    #[error("Page {requested} not found in PDF (Total pages: {total})")]
    PageNotFound {
        requested: usize,
        total: usize,
    },
    #[error("PDF object error: {0}")]
    ObjectError(String),
}

/// Extracts embedded images and graphics from a specific page of a PDF document.
///
/// # Arguments
/// * `pdf_path` - The absolute filesystem path to the PDF document.
/// * `page_number` - The 1-based page index to extract from.
///
/// # Returns
/// * `Result<(Vec<String>, usize, usize), PdfError>` - On success, returns a tuple containing:
///   1. A vector of base64-encoded PNG image data URLs.
///   2. The total page count of the document.
///   3. The total image count of all objects in the PDF document.
///      Or a `PdfError`.
pub fn extract_images_from_pdf_page<P: AsRef<Path>>(
    pdf_path: P,
    page_number: usize,
) -> Result<(Vec<String>, usize, usize), PdfError> {
    let pdf_path_ref = pdf_path.as_ref();
    log::info!("Rust reading file bytes for path: {:?}", pdf_path_ref);

    // Read the file bytes ourselves using standard Rust filesystem API
    let file_bytes = std::fs::read(pdf_path_ref)
        .map_err(|e| PdfError::Io {
            path: pdf_path_ref.to_string_lossy().into_owned(),
            source: e,
        })?;
    
    log::info!("Read PDF file bytes successfully. File size: {} bytes", file_bytes.len());

    // Parse the PDF from memory to bypass any file handle encoding/locking quirks in lopdf
    let mut doc = Document::load_mem(&file_bytes)
        .map_err(|e| PdfError::Parse(e.to_string()))?;

    log::info!("PDF parse successful. Total objects parsed: {}", doc.objects.len());
    log::info!("PDF encrypted: {}", doc.is_encrypted());

    // Attempt to decrypt with empty/default password if the PDF is encrypted
    if doc.is_encrypted() {
        match doc.decrypt(b"") {
            Ok(_) => log::info!("PDF successfully decrypted with default/empty password."),
            Err(e) => return Err(PdfError::DecryptionFailed(e.to_string())),
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
                let has_type_page = dict.get(b"Type").and_then(|o| o.as_name()).is_ok_and(|t| t == b"Page");
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
        .ok_or(PdfError::PageNotFound {
            requested: page_number,
            total: total_pages,
        })?;

    let page_obj = doc.get_object(*page_id).map_err(|e| PdfError::ObjectError(e.to_string()))?;
    let page_dict = page_obj.as_dict().map_err(|_| PdfError::ObjectError("Page is not a dictionary".to_string()))?;

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
            dict.get(b"Subtype").and_then(|o| o.as_name()).is_ok_and(|s| s == b"Image")
        } else if let Ok(stream) = object.as_stream() {
            stream.dict.get(b"Subtype").and_then(|o| o.as_name()).is_ok_and(|s| s == b"Image")
        } else {
            false
        }
    }).count();

    Ok((extracted_images, total_pages, total_images))
}

#[cfg(test)]
mod tests {
    use super::*;

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
