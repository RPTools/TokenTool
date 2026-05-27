// Prevents additional console window on Windows in release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod pdf_extractor;

use pdf_extractor::extract_images_from_pdf_page;

#[derive(serde::Serialize)]
struct PdfPageOutput {
    images: Vec<String>,
    total_pages: usize,
    total_images: usize,
}

#[tauri::command]
fn extract_pdf_images(pdf_path: String, page_number: usize) -> Result<PdfPageOutput, String> {
    let file_name = std::path::Path::new(&pdf_path)
        .file_name()
        .and_then(|f| f.to_str())
        .unwrap_or("Unknown PDF");
    println!("Tauri invoking PDF extraction: {} (pg {})", file_name, page_number);
    
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

    let (images, total_pages, total_images) = extract_images_from_pdf_page(pdf_path, page_number)?;
    Ok(PdfPageOutput { images, total_pages, total_images })
}


fn main() {
    tauri::Builder::default()
        // Register plugins for native OS file selection and reading
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_drag::init())
        .invoke_handler(tauri::generate_handler![extract_pdf_images])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
