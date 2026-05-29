// Prevents additional console window on Windows in release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod pdf;

use pdf::extract_images_from_pdf_page;

/// Serializable response payload returned by the `extract_pdf_images` Tauri command.
///
/// Contains the extracted image data for a single page plus document-level metadata
/// used by the frontend for pagination and status display.
#[derive(serde::Serialize)]
struct PdfPageOutput {
    /// Base64-encoded PNG data URLs for each image extracted from the requested page.
    images: Vec<String>,
    /// Total number of pages in the PDF document.
    total_pages: usize,
    /// Total number of image XObjects across the entire PDF (all pages).
    total_images: usize,
}

/// Tauri command that validates the input PDF file path and invokes the backend
/// to extract embedded graphics/images from the specified page.
///
/// # Arguments
/// * `pdf_path` - The absolute path to the PDF document.
/// * `page_number` - The 1-based index of the page to scan.
///
/// # Returns
/// * `Result<PdfPageOutput, String>` - Extracted image base64 strings and pagination metadata, or error message.
#[tauri::command]
fn extract_pdf_images(pdf_path: String, page_number: usize) -> Result<PdfPageOutput, String> {
    let file_name = std::path::Path::new(&pdf_path)
        .file_name()
        .and_then(|f| f.to_str())
        .unwrap_or("Unknown PDF");
    log::info!("Tauri invoking PDF extraction: {} (pg {})", file_name, page_number);
    
    // Perform path validation
    let path = std::path::Path::new(&pdf_path);
    
    // 1. Verify extension is `.pdf` (case-insensitive)
    if !path.extension()
        .and_then(|ext| ext.to_str())
        .map(|ext| ext.eq_ignore_ascii_case("pdf"))
        .unwrap_or(false) 
    {
        return Err("Invalid file type: File must be a PDF document.".to_string());
    }

    // 2. Verify file exists and is indeed a file
    if !path.exists() || !path.is_file() {
        return Err("File not found or is not a valid file.".to_string());
    }

    let (images, total_pages, total_images) = extract_images_from_pdf_page(pdf_path, page_number)
        .map_err(|e| e.to_string())?;
    Ok(PdfPageOutput { images, total_pages, total_images })
}


fn main() {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();

    tauri::Builder::default()
        // Register plugins for native OS file selection and reading
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_drag::init())
        .invoke_handler(tauri::generate_handler![extract_pdf_images])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
