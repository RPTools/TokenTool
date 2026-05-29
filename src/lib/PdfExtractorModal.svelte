<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { base64ToUint8Array, getBasename, isTauri, logError } from './utils';
  import PdfImageCard from './PdfImageCard.svelte';

  export let show: boolean = false;

  const dispatch = createEventDispatcher<{
    selectPortrait: { url: string };
  }>();

  let pdfPath = '';
  let currentPage = 1;
  let totalPages = 1; // Real page count loaded dynamically
  let totalImages = 0; // Real image count loaded dynamically
  let loading = false;
  let statusMessage = '';
  let images: string[] = []; // Base64 extracted images

  // Cache of selected images across pages
  // Key format: 'pageNumber_imageIndex'
  let selections: { [key: string]: { page: number; index: number; dataUrl: string } } = {};

  // Reactive count of currently selected items across all pages
  $: selectedCount = Object.keys(selections).length;

  async function selectPdf() {
    try {
      const { open } = await import('@tauri-apps/plugin-dialog');
      const selected = await open({
        filters: [{ name: 'PDF Documents', extensions: ['pdf'] }],
        multiple: false
      });

      if (selected && typeof selected === 'string') {
        pdfPath = selected;
        currentPage = 1;
        selections = {}; // Clear selections when loading a new PDF
        statusMessage = `Loaded: ${getBasename(pdfPath)}`;
        loadPageImages();
      }
    } catch (e: unknown) {
      logError(e);
      statusMessage = 'Tauri bridge unavailable. Using sample extractor.';
      mockPdfLoad();
    }
  }

  function mockPdfLoad() {
    pdfPath = 'Campaign_Adventure_Module.pdf';
    currentPage = 1;
    totalPages = 12;
    totalImages = 36; // Set a mock total images count
    selections = {}; // Reset selections on mock load
    loadPageImages();
  }

  async function loadPageImages() {
    if (!pdfPath) return;
    loading = true;
    statusMessage = `Extracting images from page ${currentPage}...`;
    images = [];

    try {
      const { invoke } = await import('@tauri-apps/api/core');
      // Call Rust backend to parse the PDF page and return page count, images, and total images (enhancement)
      const result = await invoke<{ images: string[]; total_pages: number; total_images: number }>(
        'extract_pdf_images',
        {
          pdfPath,
          pageNumber: currentPage
        }
      );

      images = result.images.map((base64) => `data:image/png;base64,${base64}`);
      totalPages = result.total_pages;
      totalImages = result.total_images;

      if (images.length === 0) {
        statusMessage = `Page ${currentPage} of ${totalPages} scanned. No images found on this page.`;
      } else {
        statusMessage = `Extracted ${images.length} images from page ${currentPage} of ${totalPages}`;
      }
    } catch (e: unknown) {
      logError('Rust PDF extraction failed:', e);
      if (isTauri) {
        statusMessage = `Extraction Error: Could not read images from PDF.`;
        images = [];
      } else {
        // Fallback for rich CSP-compliant mock data inside browser preview (N-13)
        setTimeout(() => {
          images = [
            'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="%231e1b4b"/><circle cx="50" cy="35" r="20" fill="%23818cf8"/><path d="M20 80c0-15 15-20 30-20s30 5 30 20" fill="%23818cf8"/></svg>',
            'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="%23064e3b"/><circle cx="50" cy="35" r="20" fill="%2334d399"/><path d="M20 80c0-15 15-20 30-20s30 5 30 20" fill="%2334d399"/></svg>',
            'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="%23450a0a"/><circle cx="50" cy="35" r="20" fill="%23f87171"/><path d="M20 80c0-15 15-20 30-20s30 5 30 20" fill="%23f87171"/></svg>',
            'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="%2378350f"/><circle cx="50" cy="35" r="20" fill="%23fbbf24"/><path d="M20 80c0-15 15-20 30-20s30 5 30 20" fill="%23fbbf24"/></svg>',
            'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="%231e3a8a"/><circle cx="50" cy="35" r="20" fill="%2360a5fa"/><path d="M20 80c0-15 15-20 30-20s30 5 30 20" fill="%2360a5fa"/></svg>',
            'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="%234c0519"/><circle cx="50" cy="35" r="20" fill="%23f472b6"/><path d="M20 80c0-15 15-20 30-20s30 5 30 20" fill="%23f472b6"/></svg>'
          ];
          statusMessage = `Fallback Mode: Extracted ${images.length} mockup portraits.`;
        }, 800);
      }
    } finally {
      loading = false;
    }
  }

  function handlePageInputChange(e: Event) {
    const val = parseInt((e.target as HTMLInputElement).value, 10);
    if (isNaN(val)) {
      currentPage = 1;
    } else {
      currentPage = Math.max(1, Math.min(totalPages, val));
    }
    loadPageImages();
  }

  function nextPage() {
    if (currentPage < totalPages) {
      currentPage++;
      loadPageImages();
    }
  }

  function prevPage() {
    if (currentPage > 1) {
      currentPage--;
      loadPageImages();
    }
  }

  function handleImageClick(imgUrl: string) {
    dispatch('selectPortrait', { url: imgUrl });
    show = false;
  }



  // --- NEW ENHANCEMENTS FOR FILE IMPORT/EXPORT ---

  // Toggle selection for a thumbnail card index (fully reactive Svelte state across pages)
  function toggleSelectImage(index: number) {
    const key = `${currentPage}_${index}`;
    if (selections[key]) {
      delete selections[key];
    } else {
      selections[key] = {
        page: currentPage,
        index: index,
        dataUrl: images[index]
      };
    }
    selections = selections; // Trigger Svelte reactive compiler update
  }

  // Toggle select/deselect all images on the current page
  function toggleSelectAll() {
    const allSelectedOnPage =
      images.length > 0 && images.every((_, i) => selections[`${currentPage}_${i}`]);
    if (allSelectedOnPage) {
      for (let i = 0; i < images.length; i++) {
        delete selections[`${currentPage}_${i}`];
      }
    } else {
      for (let i = 0; i < images.length; i++) {
        selections[`${currentPage}_${i}`] = {
          page: currentPage,
          index: i,
          dataUrl: images[i]
        };
      }
    }
    selections = selections; // Trigger Svelte reactive compiler update
  }

  // Save a single extracted image directly to a user-selected path
  async function saveSingleImage(imgDataUrl: string, index: number) {
    try {
      const { save } = await import('@tauri-apps/plugin-dialog');
      const { writeFile } = await import('@tauri-apps/plugin-fs');

      const rawPdfName = getBasename(pdfPath).replace('.pdf', '') || 'extracted';
      // Sanitize pdfName to prevent traversal and illegal characters (H-3 / L-6)
      const pdfName = rawPdfName.replace(/[\\/:*?"<>|]/g, '_').trim() || 'extracted';
      const defaultFilename = `${pdfName}_pg${currentPage}_img${index + 1}.png`;

      const selectedPath = await save({
        defaultPath: defaultFilename,
        filters: [{ name: 'PNG Images', extensions: ['png'] }]
      });

      if (selectedPath) {
        // Extract raw base64 data bytes with split guard (L-2)
        const parts = imgDataUrl.split(',');
        if (parts.length < 2) {
          logError('Malformed image data URL');
          return;
        }
        const base64Data = parts[1];
        const bytes = base64ToUint8Array(base64Data);
        await writeFile(selectedPath, bytes);
        statusMessage = `Successfully saved image to: ${getBasename(selectedPath)}`;
      }
    } catch (e: unknown) {
      logError('Failed to save image:', e);
      statusMessage = `Save failed: Could not write image file.`;
    }
  }

  // Bulk save all selected images from ALL pages to a user-selected directory folder
  async function saveSelectedImages() {
    if (selectedCount === 0) return;

    try {
      const { open } = await import('@tauri-apps/plugin-dialog');
      const { writeFile } = await import('@tauri-apps/plugin-fs');

      // Open directory selection dialog
      const selectedDir = await open({
        directory: true,
        multiple: false
      });

      if (selectedDir && typeof selectedDir === 'string') {
        loading = true;
        statusMessage = `Saving ${selectedCount} selected images to folder...`;

        const rawPdfName = getBasename(pdfPath).replace('.pdf', '') || 'extracted';
        // Sanitize pdfName to prevent traversal and illegal characters (H-3 / L-6)
        const pdfName = rawPdfName.replace(/[\\/:*?"<>|]/g, '_').trim() || 'extracted';
        
        const { join } = await import('@tauri-apps/api/path');

        let savedCount = 0;
        for (const key of Object.keys(selections)) {
          const item = selections[key];
          const filename = `${pdfName}_pg${item.page}_img${item.index + 1}.png`;
          const filePath = await join(selectedDir, filename);

          // Extract raw base64 data bytes with split guard (L-2)
          const parts = item.dataUrl.split(',');
          if (parts.length < 2) {
            logError(`Malformed image data URL for key: ${key}`);
            continue;
          }
          const base64Data = parts[1];
          const bytes = base64ToUint8Array(base64Data);
          await writeFile(filePath, bytes);
          savedCount++;
        }

        // Reset selection cache after successful bulk export
        selections = {};
        statusMessage = `Successfully saved ${savedCount} images to folder!`;
      }
    } catch (e: unknown) {
      logError('Bulk save failed:', e);
      statusMessage = `Bulk save failed: Could not write files to directory.`;
    } finally {
      loading = false;
    }
  }
</script>

{#if show}
  <div
    class="modal-backdrop flex overflow-y-auto p-6 select-none"
    on:click|self={() => (show = false)}
    on:keydown={(e) => {
      if (e.key === 'Escape') show = false;
    }}
    role="presentation"
  >
    <div class="modal-container animate-modal-enter" role="dialog" aria-modal="true" aria-labelledby="pdf-modal-title">
      <!-- Top Header -->
      <div
        class="flex justify-between items-center px-6 py-4 border-b border-[#2e3440] bg-[#15181f]"
      >
        <div class="flex items-center gap-3">
          <svg
            class="w-6 h-6 text-violet-500"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1.0 01.707.293l5.414 5.414a1 1.0 01.293.707V19a2 2 0 01-2 2z"
            ></path>
          </svg>
          <div>
            <h2 id="pdf-modal-title" class="text-lg font-bold text-slate-100 font-outfit">PDF Image Extractor</h2>
            <p class="text-xs text-slate-500">
              Extract maps & character portraits directly from campaign booklets
            </p>
          </div>
        </div>

        <button on:click={() => (show = false)} class="close-button" aria-label="Close Modal">
          <svg
            class="w-5 h-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M6 18L18 6M6 6l12 12"
            ></path>
          </svg>
        </button>
      </div>

      <!-- Action Panel (Clean 2-row, 3-column layout) -->
      <div class="action-panel-grid">
        <!-- ROW 1, COL 1: Select PDF File Button -->
        <button on:click={selectPdf} class="select-pdf-button flex items-center justify-center">
          <svg
            class="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"
            ></path>
          </svg>
          Select PDF File
        </button>

        <!-- ROW 1, COL 2: Page Counter Box -->
        <div>
          {#if pdfPath}
            <div
              class="page-counter-box flex items-center justify-center gap-2 bg-[#1b1f28] border border-[#2e3440] px-3 rounded-lg text-xs font-semibold w-[13rem]"
            >
              <span class="text-slate-400 select-none">Page:</span>
              <button
                on:click={prevPage}
                disabled={currentPage <= 1}
                class="page-nav-button font-bold">&lt;</button
              >
              <input
                type="number"
                bind:value={currentPage}
                on:change={handlePageInputChange}
                min="1"
                max={totalPages}
                class="w-12 bg-transparent text-center focus:outline-none text-violet-400 font-bold text-xs"
              />
              <span class="text-slate-500 select-none">/ {totalPages}</span>
              <button
                on:click={nextPage}
                disabled={currentPage >= totalPages}
                class="page-nav-button font-bold">&gt;</button
              >
            </div>
          {/if}
        </div>

        <!-- ROW 1, COL 3: Select Page & Save Selected Buttons -->
        <div>
          {#if pdfPath}
            <div class="flex items-center gap-3">
              <button
                on:click={toggleSelectAll}
                disabled={images.length === 0}
                class="select-page-btn flex items-center justify-center gap-2 px-3 bg-[#1b1f28] border border-[#2e3440] hover:bg-[#252a35] hover:text-slate-100 text-slate-300 font-semibold text-xs rounded-lg transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed w-[7.5rem] text-center focus:outline-none focus:ring-0"
              >
                {images.length > 0 && images.every((_, i) => selections[`${currentPage}_${i}`])
                  ? 'Deselect Page'
                  : 'Select Page'}
              </button>

              {#if selectedCount > 0}
                <button
                  on:click={saveSelectedImages}
                  class="save-selected-button animate-pulse-glow flex items-center justify-center"
                >
                  <svg
                    class="w-4 h-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4"
                    ></path>
                  </svg>
                  Save Selected ({selectedCount})
                </button>
              {/if}
            </div>
          {/if}
        </div>

        <!-- ROW 2, COL 1: Empty space below Select PDF button -->
        <div></div>

        <!-- ROW 2, COL 2: Graphics in PDF count -->
        <div>
          {#if pdfPath}
            <span class="text-[10px] text-slate-500 font-mono pl-1 leading-none">
              Graphics in PDF: {totalImages}
            </span>
          {/if}
        </div>

        <!-- ROW 2, COL 3: Extracted Status text -->
        <div>
          {#if pdfPath}
            <span class="text-[10px] text-slate-500 font-mono pl-1 leading-none">
              {statusMessage}
            </span>
          {/if}
        </div>
      </div>

      <!-- Content Area -->
      <div class="modal-content-area">
        {#if loading}
          <div class="loading-state">
            <div class="spinner">
              <div class="spinner-inner-track"></div>
              <div class="spinner-inner-active"></div>
            </div>
            <p class="text-slate-300 font-semibold">Extracting embedded graphics...</p>
            <p class="text-xs text-slate-500 mt-1 font-mono">
              Scanning XObjects & Annotations appearances
            </p>
          </div>
        {:else if images.length > 0}
          <div class="images-grid" role="list">
            {#each images as img, i (img)}
              <PdfImageCard
                imgUrl={img}
                index={i}
                selected={!!selections[`${currentPage}_${i}`]}
                on:toggleSelect={(e) => toggleSelectImage(e.detail.index)}
                on:useAsPortrait={(e) => handleImageClick(e.detail.url)}
                on:saveImage={(e) => saveSingleImage(e.detail.url, e.detail.index)}
              />
            {/each}
          </div>
        {:else}
          <div class="empty-state">
            <svg
              class="w-16 h-16 mb-4 text-[#272e3a]"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.5"
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
              ></path>
            </svg>
            <h3 class="text-slate-400 font-semibold text-sm">
              {pdfPath ? 'No Graphics Found' : 'No PDF Loaded'}
            </h3>
            <p class="text-xs text-slate-600 max-w-sm mt-1">
              {pdfPath
                ? `Page ${currentPage} of ${totalPages} does not seem to contain raw embedded raster graphics or interactive appearances. Try navigating to another page!`
                : 'Load a Campaign or Module PDF to scan and extract high-resolution character graphics, maps, and interactive button overlays.'}
            </p>
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .action-panel-grid {
    display: grid;
    grid-template-columns: 10.5rem 14rem 1fr;
    align-items: center;
    column-gap: 1rem;
    row-gap: 0.375rem;
    padding: 1rem 1.5rem;
    border-bottom: 1px solid #2e3440;
    background-color: #13161c;
  }

  .modal-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background-color: rgba(10, 11, 15, 0.75);
    backdrop-filter: blur(12px);
    z-index: 100;
  }

  .modal-container {
    position: relative;
    display: flex;
    flex-direction: column;
    margin: auto;
    width: 100%;
    max-width: 56rem; /* max-w-4xl */
    height: 85vh; /* h-[85vh] */
    background-color: #111318;
    border: 1px solid #2e3440;
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
    border-radius: 1rem;
    overflow: hidden;
  }

  .close-button {
    padding: 0.5rem;
    background-color: transparent;
    border: none;
    color: #94a3b8;
    border-radius: 0.5rem;
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .close-button:hover {
    background-color: #252a35;
    color: #f8fafc;
  }

  .select-pdf-button,
  .save-selected-button,
  .page-counter-box,
  .select-page-btn {
    height: 2.375rem !important; /* exactly 38px */
    box-sizing: border-box !important;
  }

  .select-pdf-button {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    background-color: #7c3aed;
    color: #ffffff;
    font-weight: 600;
    font-size: 0.75rem;
    border: none;
    border-radius: 0.5rem;
    cursor: pointer;
    box-shadow: 0 4px 6px -1px rgba(124, 58, 237, 0.2);
    transition: all 0.2s ease;
  }

  .select-pdf-button:hover {
    background-color: #8b5cf6;
  }

  .select-pdf-button:active {
    transform: scale(0.95);
  }

  .page-nav-button {
    padding: 0.25rem 0.5rem;
    background-color: transparent;
    border: none;
    color: #cbd5e1;
    cursor: pointer;
    border-radius: 0.25rem;
    transition: all 0.2s ease;
  }

  .page-nav-button:hover:not(:disabled) {
    background-color: #2b313f;
  }

  .page-nav-button:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }

  .modal-content-area {
    flex: 1;
    overflow-y: auto;
    padding: 1.5rem;
    background-color: #0f1115;
  }

  .loading-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    text-align: center;
  }

  .spinner {
    position: relative;
    width: 4rem;
    height: 4rem;
    margin-bottom: 1rem;
  }

  .spinner-inner-track {
    position: absolute;
    top: 0;
    left: 0;
    width: 4rem;
    height: 4rem;
    border: 4px solid rgba(124, 58, 237, 0.15);
    border-radius: 9999px;
  }

  .spinner-inner-active {
    position: absolute;
    top: 0;
    left: 0;
    width: 4rem;
    height: 4rem;
    border: 4px solid transparent;
    border-top-color: #8b5cf6;
    border-radius: 9999px;
    animation: spin 1s linear infinite;
  }

  .images-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1.5rem;
  }

  @media (min-width: 768px) {
    .images-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  @media (min-width: 1024px) {
    .images-grid {
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }
  }





  .save-selected-button {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    background: linear-gradient(135deg, #7c3aed 0%, #4f46e5 100%);
    color: #ffffff;
    font-weight: 600;
    font-size: 0.75rem;
    border: 1px solid #7c3aed;
    border-radius: 0.5rem;
    cursor: pointer;
    box-shadow: 0 4px 12px rgba(124, 58, 237, 0.3);
    transition: all 0.2s ease;
  }

  .save-selected-button:hover {
    background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%);
    border-color: #8b5cf6;
    box-shadow: 0 6px 16px rgba(124, 58, 237, 0.4);
  }

  .save-selected-button:active {
    transform: scale(0.95);
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    text-align: center;
    padding: 2rem;
    border: 1px dashed #272e3a;
    border-radius: 1rem;
  }

  @keyframes modalEnter {
    from {
      opacity: 0;
      transform: scale(0.95) translateY(10px);
    }
    to {
      opacity: 1;
      transform: scale(1) translateY(0);
    }
  }

  .animate-modal-enter {
    animation: modalEnter 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  }

  @keyframes pulseGlow {
    0%,
    100% {
      box-shadow: 0 0 10px rgba(139, 92, 246, 0.25);
      border-color: rgba(139, 92, 246, 0.4);
    }
    50% {
      box-shadow: 0 0 20px rgba(139, 92, 246, 0.55);
      border-color: rgba(139, 92, 246, 0.8);
    }
  }

  .animate-pulse-glow {
    animation: pulseGlow 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
  }

  input[type='number']::-webkit-inner-spin-button,
  input[type='number']::-webkit-outer-spin-button {
    -webkit-appearance: none;
    margin: 0;
  }

  ::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }

  ::-webkit-scrollbar-track {
    background: #0f1115;
  }

  ::-webkit-scrollbar-thumb {
    background: #272e3a;
    border-radius: 3px;
  }

  ::-webkit-scrollbar-thumb:hover {
    background: #434c5e;
  }

  @keyframes spin {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }
</style>
