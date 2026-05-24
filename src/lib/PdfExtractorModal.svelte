<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  export let show: boolean = false;

  const dispatch = createEventDispatcher();

  let pdfPath = '';
  let currentPage = 1;
  let totalPages = 1; // Real page count loaded dynamically
  let loading = false;
  let statusMessage = '';
  let images: string[] = []; // Base64 extracted images

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
        statusMessage = `Loaded: ${pdfPath.split('\\').pop()}`;
        loadPageImages();
      }
    } catch (e) {
      console.error(e);
      statusMessage = "Tauri bridge unavailable. Using sample extractor.";
      mockPdfLoad();
    }
  }

  function mockPdfLoad() {
    pdfPath = "Campaign_Adventure_Module.pdf";
    currentPage = 1;
    totalPages = 12;
    loadPageImages();
  }

  async function loadPageImages() {
    if (!pdfPath) return;
    loading = true;
    statusMessage = `Extracting images from page ${currentPage}...`;
    images = [];

    try {
      const { invoke } = await import('@tauri-apps/api/core');
      // Call Rust backend to parse the PDF page and return page count + images
      const result = await invoke<{ images: string[], total_pages: number }>('extract_pdf_images', {
        pdfPath,
        pageNumber: currentPage
      });
      
      images = result.images.map(base64 => `data:image/png;base64,${base64}`);
      totalPages = result.total_pages;
      
      if (images.length === 0) {
        statusMessage = `Page ${currentPage} of ${totalPages} scanned. No images found on this page.`;
      } else {
        statusMessage = `Extracted ${images.length} images from page ${currentPage} of ${totalPages}`;
      }
    } catch (e) {
      console.error('Rust PDF extraction failed:', e);
      const isTauri = typeof window !== 'undefined' && (window as any).__TAURI_INTERNALS__ !== undefined;
      
      if (isTauri) {
        statusMessage = `Extraction Error: ${e}`;
        images = [];
      } else {
        // Fallback for rich mock data inside browser preview
        setTimeout(() => {
          images = [
            'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
            'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
            'https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
            'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
            'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
            'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3'
          ];
          statusMessage = `Fallback Mode: Extracted ${images.length} mockup portraits.`;
        }, 800);
      }
    } finally {
      loading = false;
    }
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

  function handleDragStart(e: DragEvent, imgUrl: string) {
    if (e.dataTransfer) {
      e.dataTransfer.setData('text/plain', imgUrl);
      e.dataTransfer.setData('url', imgUrl);
      e.dataTransfer.effectAllowed = 'copy';
    }
  }
</script>

{#if show}
  <div 
    class="modal-backdrop flex items-center justify-center p-6 select-none" 
    on:click|self={() => show = false}
    on:keydown={(e) => { if (e.key === 'Escape') show = false; }}
    role="button"
    tabindex="-1"
  >
    <div class="modal-container animate-modal-enter">
      
      <!-- Top Header -->
      <div class="flex justify-between items-center px-6 py-4 border-b border-[#2e3440] bg-[#15181f]">
        <div class="flex items-center gap-3">
          <svg class="w-6 h-6 text-violet-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1.0 01.707.293l5.414 5.414a1 1.0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          <div>
            <h2 class="text-lg font-bold text-slate-100 font-outfit">PDF Image Extractor</h2>
            <p class="text-xs text-slate-500">Extract maps & character portraits directly from campaign booklets</p>
          </div>
        </div>
        
        <button 
          on:click={() => show = false}
          class="close-button"
          aria-label="Close Modal"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>
      </div>

      <!-- Action Panel -->
      <div class="flex flex-wrap items-center gap-4 px-6 py-3 border-b border-[#2e3440] bg-[#13161c]">
        <button 
          on:click={selectPdf}
          class="select-pdf-button"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
          Select PDF File
        </button>

        {#if pdfPath}
          <div class="flex items-center gap-2 bg-[#1b1f28] border border-[#2e3440] px-3 py-1.5 rounded-lg text-sm">
            <span class="text-slate-400 font-medium">Page:</span>
            <button on:click={prevPage} disabled={currentPage <= 1} class="page-nav-button">&lt;</button>
            <input 
              type="number" 
              bind:value={currentPage} 
              on:change={loadPageImages}
              min="1" 
              max={totalPages} 
              class="w-12 bg-transparent text-center focus:outline-none text-violet-400 font-semibold"
            />
            <span class="text-slate-500">/ {totalPages}</span>
            <button on:click={nextPage} disabled={currentPage >= totalPages} class="page-nav-button">&gt;</button>
          </div>
        {/if}

        <span class="text-xs text-slate-400 ml-auto font-mono">{statusMessage}</span>
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
            <p class="text-xs text-slate-500 mt-1 font-mono">Scanning XObjects & Annotations appearances</p>
          </div>
        {:else if images.length > 0}
          <div class="images-grid" role="list">
            {#each images as img, i}
              <div 
                class="image-card"
                draggable="true"
                role="listitem"
                on:dragstart={(e) => handleDragStart(e, img)}
              >
                <!-- Thumbnail -->
                <div class="aspect-square bg-slate-900 overflow-hidden flex items-center justify-center">
                  <img 
                    src={img} 
                    alt="Extracted resource {i}"
                    class="w-full h-full object-cover hover-scale"
                  />
                </div>

                <!-- Hover Overlay actions -->
                <div class="hover-overlay">
                  <button 
                    on:click={() => handleImageClick(img)}
                    class="use-portrait-button"
                  >
                    Use as Portrait
                  </button>
                  <p class="text-[10px] text-center text-slate-500 mt-2">Drag directly to Canvas</p>
                </div>
              </div>
            {/each}
          </div>
        {:else}
          <div class="empty-state">
            <svg class="w-16 h-16 mb-4 text-[#272e3a]" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
            </svg>
            <h3 class="text-slate-400 font-semibold text-sm">
              {pdfPath ? "No Graphics Found" : "No PDF Loaded"}
            </h3>
            <p class="text-xs text-slate-600 max-w-sm mt-1">
              {pdfPath 
                ? `Page ${currentPage} of ${totalPages} does not seem to contain raw embedded raster graphics or interactive appearances. Try navigating to another page!` 
                : "Load a Campaign or Module PDF to scan and extract high-resolution character graphics, maps, and interactive button overlays."
              }
            </p>
          </div>
        {/if}
      </div>
      
    </div>
  </div>
{/if}

<style>
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

  .select-pdf-button {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    background-color: #7c3aed;
    color: #ffffff;
    font-weight: 500;
    font-size: 0.875rem;
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

  .image-card {
    position: relative;
    background-color: #161a22;
    border: 1px solid #272e3a;
    border-radius: 0.75rem;
    overflow: hidden;
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);
    transition: all 0.3s ease;
  }

  .image-card:hover {
    border-color: rgba(139, 92, 246, 0.6);
  }

  .image-card:active {
    transform: scale(0.95);
  }

  .hover-scale {
    transition: transform 0.5s ease;
  }

  .image-card:hover .hover-scale {
    transform: scale(1.05);
  }

  .hover-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: rgba(15, 17, 21, 0.85);
    opacity: 0;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    padding: 0.75rem;
    transition: opacity 0.3s ease;
    pointer-events: none;
  }

  .image-card:hover .hover-overlay {
    opacity: 1;
    pointer-events: auto;
  }

  .use-portrait-button {
    width: 100%;
    padding: 0.5rem 0;
    background-color: #7c3aed;
    color: #ffffff;
    font-size: 0.75rem;
    font-weight: 600;
    border: none;
    border-radius: 0.5rem;
    cursor: pointer;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.2);
    transition: background-color 0.2s ease;
  }

  .use-portrait-button:hover {
    background-color: #8b5cf6;
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

  input[type="number"]::-webkit-inner-spin-button,
  input[type="number"]::-webkit-outer-spin-button {
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
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
</style>
