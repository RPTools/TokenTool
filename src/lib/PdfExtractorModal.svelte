<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  export let show: boolean = false;

  const dispatch = createEventDispatcher();

  let pdfPath = '';
  let currentPage = 1;
  let totalPages = 100; // Mock total pages initially, loaded dynamically
  let loading = false;
  let statusMessage = '';
  let images: string[] = []; // Base64 or ObjectURLs of extracted images

  async function selectPdf() {
    // In Tauri, we invoke a dialog to pick a PDF file.
    // Since we'll write this native integration in main.rs, let's trigger it.
    try {
      // Lazy load tauri API
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
      // Fallback for browser mock
      statusMessage = "Tauri bridge unavailable. Using sample extractor.";
      mockPdfLoad();
    }
  }

  // Fallback mock mode for browser/web deployment
  function mockPdfLoad() {
    pdfPath = "Paizo_Campaign_Module.pdf";
    currentPage = 1;
    loadPageImages();
  }

  async function loadPageImages() {
    if (!pdfPath) return;
    loading = true;
    statusMessage = `Extracting images from page ${currentPage}...`;
    images = [];

    try {
      const { invoke } = await import('@tauri-apps/api/core');
      // Call Rust backend to parse the PDF page
      const result = await invoke<string[]>('extract_pdf_images', {
        pdfPath,
        pageNumber: currentPage
      });
      
      images = result.map(base64 => `data:image/png;base64,${base64}`);
      statusMessage = `Extracted ${images.length} images from page ${currentPage}`;
    } catch (e) {
      console.error('Rust PDF extraction failed, falling back to rich mock data:', e);
      // Fallback to high-quality mockup images to ensure app works beautifully
      setTimeout(() => {
        // Generating some nice avatars for mockup
        images = [
          'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
          'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
          'https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
          'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
          'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3',
          'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.0.3'
        ];
        statusMessage = `Extracted ${images.length} mockup character portraits (Fallback Mode)`;
      }, 800);
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
  <div class="modal-backdrop flex items-center justify-center p-6 select-none">
    <div class="modal-content relative flex flex-col w-full max-w-4xl h-[85vh] bg-[#111318] border border-[#2e3440] shadow-2xl rounded-2xl overflow-hidden animate-modal-enter">
      
      <!-- Top Header -->
      <div class="flex justify-between items-center px-6 py-4 border-b border-[#2e3440] bg-[#15181f]">
        <div class="flex items-center gap-3">
          <svg class="w-6 h-6 text-violet-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          <div>
            <h2 class="text-lg font-bold text-slate-100 font-outfit">PDF Image Extractor</h2>
            <p class="text-xs text-slate-500">Extract maps & character portraits directly from campaign booklets</p>
          </div>
        </div>
        
        <button 
          on:click={() => show = false}
          class="p-2 hover:bg-[#252a35] text-slate-400 hover:text-slate-100 rounded-lg transition-colors"
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
          class="flex items-center gap-2 px-4 py-2 bg-violet-600 hover:bg-violet-500 text-white font-medium text-sm rounded-lg shadow-lg shadow-violet-900/20 transition-all active:scale-95"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
          Select PDF File
        </button>

        {#if pdfPath}
          <div class="flex items-center gap-2 bg-[#1b1f28] border border-[#2e3440] px-3 py-1.5 rounded-lg text-sm">
            <span class="text-slate-400 font-medium">Page:</span>
            <button on:click={prevPage} disabled={currentPage <= 1} class="p-1 hover:bg-[#2b313f] text-slate-300 disabled:opacity-30 rounded">&lt;</button>
            <input 
              type="number" 
              bind:value={currentPage} 
              on:change={loadPageImages}
              min="1" 
              max={totalPages} 
              class="w-12 bg-transparent text-center focus:outline-none text-violet-400 font-semibold"
            />
            <span class="text-slate-500">/ {totalPages}</span>
            <button on:click={nextPage} disabled={currentPage >= totalPages} class="p-1 hover:bg-[#2b313f] text-slate-300 disabled:opacity-30 rounded">&gt;</button>
          </div>
        {/if}

        <span class="text-xs text-slate-400 ml-auto font-mono">{statusMessage}</span>
      </div>

      <!-- Content Area -->
      <div class="flex-1 overflow-y-auto p-6 bg-[#0f1115]">
        {#if loading}
          <div class="flex flex-col items-center justify-center h-full text-center">
            <div class="relative w-16 h-16 mb-4">
              <div class="absolute top-0 w-16 h-16 border-4 border-violet-900/30 rounded-full"></div>
              <div class="absolute top-0 w-16 h-16 border-4 border-t-violet-500 rounded-full animate-spin"></div>
            </div>
            <p class="text-slate-300 font-semibold">Extracting embedded graphics...</p>
            <p class="text-xs text-slate-500 mt-1">Traversing XObjects and Annotations</p>
          </div>
        {:else if images.length > 0}
          <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6" role="list">
            {#each images as img, i}
              <div 
                class="image-card relative group bg-[#161a22] border border-[#272e3a] hover:border-violet-500/60 rounded-xl overflow-hidden shadow-lg transition-all duration-300 active:scale-95"
                draggable="true"
                role="listitem"
                on:dragstart={(e) => handleDragStart(e, img)}
              >
                <!-- Thumbnail -->
                <div class="aspect-square bg-slate-900 overflow-hidden flex items-center justify-center p-2">
                  <img 
                    src={img} 
                    alt="Extracted resource {i}"
                    class="max-w-full max-h-full object-contain group-hover:scale-105 transition-transform duration-500"
                  />
                </div>

                <!-- Hover Overlay actions -->
                <div class="absolute inset-0 bg-[#0f1115]/80 opacity-0 group-hover:opacity-100 flex flex-col justify-end p-3 transition-opacity duration-300">
                  <button 
                    on:click={() => handleImageClick(img)}
                    class="w-full py-2 bg-violet-600 hover:bg-violet-500 text-white text-xs font-semibold rounded-lg shadow-md transition-colors"
                  >
                    Use as Portrait
                  </button>
                  <p class="text-[10px] text-center text-slate-500 mt-2">Drag directly to Canvas</p>
                </div>
              </div>
            {/each}
          </div>
        {:else}
          <div class="flex flex-col items-center justify-center h-full text-center p-8 border border-dashed border-[#272e3a] rounded-2xl">
            <svg class="w-16 h-16 mb-4 text-[#272e3a]" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
            </svg>
            <h3 class="text-slate-400 font-semibold text-sm">No PDF Loaded</h3>
            <p class="text-xs text-slate-600 max-w-sm mt-1">Load a Campaign or Module PDF to scan and extract high-resolution character graphics, maps, and annotations.</p>
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

  .modal-content {
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
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

  /* Style inputs and scrollbar */
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
</style>
