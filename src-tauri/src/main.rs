// Prevents additional console window on Windows in release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod pdf_extractor;

use pdf_extractor::extract_images_from_pdf_page;

#[tauri::command]
fn extract_pdf_images(pdf_path: String, page_number: usize) -> Result<Vec<String>, String> {
    println!("Tauri invoking PDF extraction: {} (pg {})", pdf_path, page_number);
    extract_images_from_pdf_page(pdf_path, page_number)
}

fn main() {
    tauri::Builder::default()
        // Register plugins for native OS file selection and reading
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .invoke_handler(tauri::generate_handler![extract_pdf_images])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
