<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import TokenCanvas from './lib/TokenCanvas.svelte';
  import PdfExtractorModal from './lib/PdfExtractorModal.svelte';
  import manifest from './lib/overlayManifest.json';
  import { parseTokenPsd } from './lib/psdParser';
  import { dataUrlToUint8Array, getBasename } from './lib/utils';

  interface OverlayPreset {
    name: string;
    path: string;
    type?: string;
    thumbPath?: string;
  }

  interface CustomOverlay {
    name: string;
    mask: string | null;
    overlay: string;
  }

  // Parse Categories from Manifest
  const overlayCategories = Object.keys(manifest.categories);
  let selectedCategory =
    overlayCategories.find((c) => c.includes('Round/Smooth')) || overlayCategories[0] || '';
  $: currentCategoryOverlays = manifest.categories[selectedCategory] || [];

  // State binds
  let portraitUrl: string | null = null;
  let maskUrl: string | null = null;
  let overlayUrl: string | null = null;
  let activeOverlayId = '';

  // Custom Overlays loaded from system
  let customOverlays: CustomOverlay[] = [];

  // Canvas settings
  let size = 256;
  let bgColor = '#00000000'; // Default transparent background
  let zoom = 1.0;
  let rotation = 0;
  let transparency = 1.0;
  let blur = 0;
  let glow = 0;
  let overlayOpacity = 1.0;
  let clipPortrait = true;

  // Export options
  let fileName = 'token';
  let useFileNumbering = true;
  let fileSuffix = 1;

  // UI state
  let canvasRef: TokenCanvas;
  let showPdfExtractor = false;
  let loadingPsd = false;
  let errorMessage = '';
  let mounted = false;

  const sizeOptions = [128, 256, 512, 1024];

  // Preset VTT Colors
  const presetColors = [
    { name: 'Transparent', hex: '#00000000' },
    { name: 'Dark Slate', hex: '#1e293b' },
    { name: 'Deep Crimson', hex: '#991b1b' },
    { name: 'Emerald', hex: '#065f46' },
    { name: 'Abyssal Blue', hex: '#1e3a8a' },
    { name: 'Obsidian Black', hex: '#000000' }
  ];

  // Initialize defaults
  onMount(() => {
    // Check if we have standard settings or load history
    const stored = localStorage.getItem('tokentool_settings');
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        size = parsed.size || 256;
        bgColor = parsed.bgColor || '#00000000';
        fileName = parsed.fileName || 'token';
        useFileNumbering = parsed.useFileNumbering ?? true;
        fileSuffix = parsed.fileSuffix || 1;
      } catch (e: unknown) {
        console.error('Failed to parse cached settings', e);
      }
    }

    // Load first default overlay
    if (currentCategoryOverlays.length > 0) {
      handleSelectPreset(currentCategoryOverlays[0]);
    }
    mounted = true;
  });

  onDestroy(() => {
    // Revoke portraitUrl if it's a blob URL
    if (portraitUrl && portraitUrl.startsWith('blob:')) {
      URL.revokeObjectURL(portraitUrl);
    }
    // Revoke all custom overlay blob URLs
    for (const custom of customOverlays) {
      if (custom.overlay && custom.overlay.startsWith('blob:')) {
        URL.revokeObjectURL(custom.overlay);
      }
      if (custom.mask && custom.mask.startsWith('blob:')) {
        URL.revokeObjectURL(custom.mask);
      }
    }
  });

  // Save settings automatically on adjustment
  $: if (mounted) {
    localStorage.setItem(
      'tokentool_settings',
      JSON.stringify({
        size,
        bgColor,
        fileName,
        useFileNumbering,
        fileSuffix
      })
    );
  }

  // Handle Load Portrait File
  async function selectPortraitFile() {
    try {
      const { open } = await import('@tauri-apps/plugin-dialog');
      const selected = await open({
        filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }],
        multiple: false
      });

      if (selected && typeof selected === 'string') {
        const { readFile } = await import('@tauri-apps/plugin-fs');
        const data = await readFile(selected);
        const blob = new Blob([data]);

        // Revoke the old object URL to prevent memory leaks (M-5)
        if (portraitUrl && portraitUrl.startsWith('blob:')) {
          URL.revokeObjectURL(portraitUrl);
        }

        portraitUrl = URL.createObjectURL(blob);
      }
    } catch {
      // Browser fallback (mock or standard input click)
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = 'image/png,image/jpeg,image/webp';
      input.onchange = (e) => {
        const file = (e.target as HTMLInputElement).files?.[0];
        if (file) {
          const reader = new FileReader();
          reader.onload = () => {
            // Revoke old object URL (M-5)
            if (portraitUrl && portraitUrl.startsWith('blob:')) {
              URL.revokeObjectURL(portraitUrl);
            }
            portraitUrl = reader.result as string;
          };
          reader.readAsDataURL(file);
        }
      };
      input.click();
    }
  }

  // Helper to commit a custom overlay to the state and select it
  function commitCustomOverlay(name: string, mask: string | null, overlay: string) {
    // Check for duplicates and clean them up immediately to prevent leaks (N-4)
    const existingIndex = customOverlays.findIndex(c => c.name === name);
    if (existingIndex !== -1) {
      const removed = customOverlays[existingIndex];
      if (removed.overlay && removed.overlay.startsWith('blob:')) {
        URL.revokeObjectURL(removed.overlay);
      }
      if (removed.mask && removed.mask.startsWith('blob:')) {
        URL.revokeObjectURL(removed.mask);
      }
      customOverlays.splice(existingIndex, 1);
    }

    // Limit list to a maximum of 10 items to prevent unbounded session memory growth (N-4)
    const MAX_CUSTOM_OVERLAYS = 10;
    if (customOverlays.length >= MAX_CUSTOM_OVERLAYS) {
      const oldest = customOverlays.pop();
      if (oldest) {
        if (oldest.overlay && oldest.overlay.startsWith('blob:')) {
          URL.revokeObjectURL(oldest.overlay);
        }
        if (oldest.mask && oldest.mask.startsWith('blob:')) {
          URL.revokeObjectURL(oldest.mask);
        }
      }
    }

    customOverlays = [
      {
        name,
        mask,
        overlay
      },
      ...customOverlays
    ];

    maskUrl = mask;
    overlayUrl = overlay;
    activeOverlayId = 'custom-0'; // Fix bug: prepended item is at index 0
  }

  // Handle Custom Overlay Selection (PNG, JPG, or PSD)
  async function loadCustomOverlay() {
    let selected: string | null | string[];

    try {
      const { open } = await import('@tauri-apps/plugin-dialog');
      selected = await open({
        filters: [
          { name: 'Overlays (Image / PSD)', extensions: ['psd', 'png', 'jpg', 'jpeg', 'webp'] }
        ],
        multiple: false
      });
    } catch {
      // Tauri bridge unavailable, use browser fallback
      browserOverlayImport();
      return;
    }

    if (selected && typeof selected === 'string') {
      loadingPsd = true;
      errorMessage = '';
      try {
        const name = getBasename(selected) || 'Custom Frame';
        const { readFile } = await import('@tauri-apps/plugin-fs');
        const data = await readFile(selected);

        if (selected.toLowerCase().endsWith('.psd')) {
          const parsed = await parseTokenPsd(data.buffer);
          commitCustomOverlay(`${name} (PSD)`, parsed.mask, parsed.overlay || '');
        } else {
          // Standard Image
          const blob = new Blob([data]);
          const dataUrl = URL.createObjectURL(blob);
          commitCustomOverlay(name, null, dataUrl);
        }
      } catch (err: unknown) {
        console.error('Failed to load custom overlay:', err);
        errorMessage = err instanceof Error ? err.message : 'Failed to read or parse the overlay file.';
      } finally {
        loadingPsd = false;
      }
    }
  }

  function browserOverlayImport() {
    errorMessage = '';
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.psd,image/*';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        loadingPsd = true;
        const reader = new FileReader();
        reader.onerror = () => {
          errorMessage = 'Failed to read browser overlay file.';
          loadingPsd = false;
        };
        reader.onload = async () => {
          try {
            if (file.name.toLowerCase().endsWith('.psd')) {
              const buffer = reader.result as ArrayBuffer;
              const parsed = await parseTokenPsd(buffer);
              commitCustomOverlay(`${file.name} (PSD)`, parsed.mask, parsed.overlay || '');
            } else {
              const dataUrl = reader.result as string;
              commitCustomOverlay(file.name, null, dataUrl);
            }
          } catch (err: unknown) {
            errorMessage = err instanceof Error ? err.message : 'Failed to read browser overlay file.';
          } finally {
            loadingPsd = false;
          }
        };

        if (file.name.toLowerCase().endsWith('.psd')) {
          reader.readAsArrayBuffer(file);
        } else {
          reader.readAsDataURL(file);
        }
      }
    };
    input.click();
  }

  async function handleSelectPreset(preset: OverlayPreset) {
    activeOverlayId = preset.path;

    if (preset.type === 'psd') {
      try {
        loadingPsd = true;
        const res = await fetch(preset.path);
        if (!res.ok) throw new Error(`Failed to fetch ${preset.path}`);
        const buffer = await res.arrayBuffer();

        const extracted = await parseTokenPsd(buffer);
        if (extracted.mask && extracted.overlay) {
          maskUrl = extracted.mask;
          overlayUrl = extracted.overlay;
        } else {
          errorMessage = 'The built-in PSD is missing expected Mask/Overlay layers.';
        }
      } catch (err: unknown) {
        errorMessage = `Failed to parse built-in PSD: ${err instanceof Error ? err.message : String(err)}`;
        console.error(err);
      } finally {
        loadingPsd = false;
      }
    } else {
      maskUrl = null;
      overlayUrl = preset.path;
    }
  }

  function handleSelectCustom(custom: CustomOverlay, index: number) {
    maskUrl = custom.mask;
    overlayUrl = custom.overlay;
    activeOverlayId = `custom-${index}`;
  }

  // Handle PNG save to system disk
  async function saveToken() {
    if (!canvasRef) return;

    const dataUrl = canvasRef.exportPng();

    // Sanitize fileName to prevent directory traversal and illegal characters (L-6)
    const sanitizedBaseName = fileName.replace(/[\\/:*?"<>|]/g, '_').trim() || 'token';
    const cleanFileName = `${sanitizedBaseName}${useFileNumbering ? '_' + fileSuffix.toString().padStart(4, '0') : ''}.png`;

    try {
      const { save } = await import('@tauri-apps/plugin-dialog');
      const selectedPath = await save({
        defaultPath: cleanFileName,
        filters: [{ name: 'PNG Images', extensions: ['png'] }]
      });

      if (selectedPath) {
        const { writeFile } = await import('@tauri-apps/plugin-fs');

        const bytes = dataUrlToUint8Array(dataUrl);
        if (!bytes) {
          console.error('Failed to decode token image data');
          return;
        }

        await writeFile(selectedPath, bytes);

        if (useFileNumbering) {
          fileSuffix++;
        }
      }
    } catch {
      // Browser fallback (trigger actual anchor download)
      const link = document.createElement('a');
      link.download = cleanFileName;
      link.href = dataUrl;
      link.click();
      if (useFileNumbering) {
        fileSuffix++;
      }
    }
  }

  function handleSelectPdfPortrait(e: CustomEvent<{ url: string }>) {
    if (portraitUrl && portraitUrl.startsWith('blob:')) {
      URL.revokeObjectURL(portraitUrl);
    }
    portraitUrl = e.detail.url;
  }

  function resetAll() {
    if (canvasRef) {
      canvasRef.resetPosition();
    }
    zoom = 1.0;
    rotation = 0;
    transparency = 1.0;
    blur = 0;
    glow = 0;
    overlayOpacity = 1.0;
    clipPortrait = true;
  }
</script>

<main class="flex flex-col h-screen overflow-hidden bg-[#0f1115] text-slate-100 select-none">
  <!-- Gorgeous App Header -->
  <header
    class="flex justify-between items-center px-8 py-4 border-b border-[#1f232e] bg-[#12141a]"
  >
    <div class="flex items-center gap-3">
      <div
        class="relative w-8 h-8 rounded-lg bg-gradient-to-tr from-violet-600 to-fuchsia-600 flex items-center justify-center shadow-lg shadow-violet-900/40"
      >
        <span class="text-white font-extrabold text-sm font-outfit">T</span>
        <div
          class="absolute -inset-0.5 rounded-lg bg-gradient-to-tr from-violet-600 to-fuchsia-600 blur opacity-30 animate-pulse"
        ></div>
      </div>
      <div>
        <h1 class="text-lg font-bold tracking-wide font-outfit flex items-center gap-2">
          TokenTool <span
            class="text-[10px] bg-violet-600/30 border border-violet-500/30 text-violet-400 px-2 py-0.5 rounded-full font-mono font-medium"
            >v2.2.0</span
          >
        </h1>
        <p class="text-xs text-slate-500 font-medium">Next-gen desktop token framing editor</p>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-3">
      <button
        on:click={selectPortraitFile}
        class="flex items-center gap-2 px-4 py-2 bg-[#1b1f28] border border-[#2e3440] hover:border-violet-500/40 hover:bg-[#212632] text-sm text-slate-300 hover:text-slate-100 font-semibold rounded-xl shadow-md transition-all active:scale-95"
      >
        <svg
          class="w-4 h-4 text-violet-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
          ></path>
        </svg>
        Load Image
      </button>

      <button
        on:click={() => (showPdfExtractor = true)}
        class="flex items-center gap-2 px-4 py-2 bg-[#1b1f28] border border-[#2e3440] hover:border-violet-500/40 hover:bg-[#212632] text-sm text-slate-300 hover:text-slate-100 font-semibold rounded-xl shadow-md transition-all active:scale-95"
      >
        <svg
          class="w-4 h-4 text-violet-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h7a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z"
          ></path>
        </svg>
        PDF Extractor
      </button>
    </div>
  </header>

  <!-- Split Main Workspace -->
  <div class="flex-1 flex overflow-hidden">
    <!-- LEFT PANEL: Overlay libraries -->
    <aside class="w-72 border-r border-[#1f232e] bg-[#111319] flex flex-col">
      <div class="p-4 border-b border-[#1f232e] flex flex-col gap-2">
        <h3 class="text-xs font-bold uppercase tracking-wider text-slate-500 font-outfit">
          Overlay Library
        </h3>
        <button
          on:click={loadCustomOverlay}
          disabled={loadingPsd}
          class="w-full flex items-center justify-center gap-2 py-2.5 bg-[#1b1f28] border border-dashed border-[#3b4252] hover:border-violet-500/80 hover:bg-[#202531] text-xs font-bold text-violet-400 hover:text-violet-300 rounded-xl transition-all disabled:opacity-40"
        >
          {#if loadingPsd}
            <div
              class="w-3.5 h-3.5 border-2 border-t-violet-400 border-violet-900/30 rounded-full animate-spin"
            ></div>
            Parsing Layers...
          {:else}
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
                d="M12 9v3m0 0v3m0-3h3m-3 0H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z"
              ></path>
            </svg>
            Import Overlay / PSD
          {/if}
        </button>

        {#if errorMessage}
          <div
            class="text-[10px] text-rose-400 mt-1 text-center font-medium bg-rose-950/20 border border-rose-900/30 p-2 rounded-lg"
          >
            {errorMessage}
          </div>
        {/if}
      </div>

      <!-- Overlays List -->
      <div class="flex-1 overflow-y-auto p-4 flex flex-col gap-5">
        <!-- Custom imported overlays -->
        {#if customOverlays.length > 0}
          <div class="flex flex-col gap-2">
            <h4 class="text-xs font-bold text-violet-400 font-outfit uppercase tracking-wider">
              Custom / PSD Layers
            </h4>
            <div class="grid grid-cols-2 gap-3">
              {#each customOverlays as custom, index (custom.overlay)}
                <button
                  on:click={() => handleSelectCustom(custom, index)}
                  class="overlay-thumb-card group aspect-square bg-[#1a1e27] border rounded-xl p-2 flex flex-col items-center justify-center
                    {activeOverlayId === `custom-${index}`
                    ? 'border-violet-500 bg-violet-950/10 shadow-lg shadow-violet-900/10'
                    : 'border-[#2d3440] hover:border-[#434c5e]'}"
                >
                  <div class="thumb-container">
                    <img src={custom.overlay} alt={custom.name} class="thumb-image" />
                  </div>
                  <span class="thumb-label">{custom.name}</span>
                </button>
              {/each}
            </div>
          </div>
        {/if}

        <!-- Built-in Overlays -->
        <div class="flex flex-col gap-3">
          <div class="flex items-center justify-between">
            <h4 class="text-xs font-bold text-slate-400 font-outfit uppercase tracking-wider">
              Built-In Presets
            </h4>
          </div>

          <select
            bind:value={selectedCategory}
            class="bg-[#171a22] border border-[#2d3440] text-slate-300 text-xs rounded-lg px-2 py-1.5 focus:outline-none focus:border-violet-500"
          >
            {#each overlayCategories as cat (cat)}
              <option value={cat}>{cat}</option>
            {/each}
          </select>

          <div class="grid grid-cols-2 gap-3 max-h-[40vh] overflow-y-auto pr-1 custom-scrollbar">
            {#each currentCategoryOverlays as preset (preset.path)}
              <button
                on:click={() => handleSelectPreset(preset)}
                class="overlay-thumb-card group aspect-square bg-[#1a1e27] border rounded-xl p-2 flex flex-col items-center justify-center
                  {activeOverlayId === preset.path
                  ? 'border-violet-500 bg-violet-950/10 shadow-lg shadow-violet-900/10'
                  : 'border-[#2d3440] hover:border-[#434c5e]'}"
              >
                <div class="thumb-container">
                  <img
                    src={preset.thumbPath || preset.path}
                    alt={preset.name}
                    class="thumb-image"
                  />
                </div>
                <span class="thumb-label">{preset.name}</span>
              </button>
            {/each}
          </div>
        </div>
      </div>
    </aside>

    <!-- CENTER AREA: Live Interactive Canvas -->
    <section class="flex-1 flex flex-col items-center justify-center p-8 bg-[#0c0d12]">
      <!-- Interactive Screen Canvas Wrapper -->
      <div class="relative flex flex-col items-center gap-4">
        <TokenCanvas
          bind:this={canvasRef}
          bind:portraitUrl
          {maskUrl}
          {overlayUrl}
          {size}
          {bgColor}
          {transparency}
          {blur}
          {glow}
          {overlayOpacity}
          {clipPortrait}
          bind:zoom
          bind:rotation
        />

        <!-- Live Drag-Out Export cue helper -->
        <div class="flex items-center gap-2 text-xs text-slate-500 font-medium">
          <svg
            class="w-4 h-4 text-violet-500 animate-pulse"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5M7.188 2.239l.777 2.897M5.136 7.965l-2.898-.777M13.95 4.05l-2.122 2.122m-5.657 5.656l-2.12 2.122"
            ></path>
          </svg>
          Drag completed token directly to desktop, folder, or Discord!
        </div>
      </div>
    </section>

    <!-- RIGHT PANEL: Detailed control adjustment panels -->
    <aside class="w-80 border-l border-[#1f232e] bg-[#111319] overflow-y-auto flex flex-col">
      <!-- Export Panel -->
      <div class="p-5 border-b border-[#1f232e]">
        <h3 class="text-xs font-bold uppercase tracking-wider text-slate-500 font-outfit mb-3">
          Save Options
        </h3>
        <div class="flex flex-col gap-3.5">
          <div>
            <label class="block text-xs text-slate-400 font-semibold mb-1" for="filename"
              >File Name</label
            >
            <div
              class="flex items-center bg-[#171a22] border border-[#2d3440] rounded-xl overflow-hidden px-3 focus-within:border-violet-500 transition-colors"
            >
              <input
                id="filename"
                type="text"
                bind:value={fileName}
                class="flex-1 py-2 bg-transparent text-sm text-slate-200 focus:outline-none"
              />
              {#if useFileNumbering}
                <span class="text-xs text-violet-400 font-bold font-mono"
                  >_{fileSuffix.toString().padStart(4, '0')}</span
                >
              {/if}
            </div>
          </div>

          <label
            class="flex items-center gap-2.5 text-xs text-slate-400 font-semibold cursor-pointer"
          >
            <input
              type="checkbox"
              bind:checked={useFileNumbering}
              class="accent-violet-500 w-4 h-4 rounded"
            />
            Incremental Suffix Numbering
          </label>

          <button
            on:click={saveToken}
            class="w-full py-2.5 bg-violet-600 hover:bg-violet-500 text-white font-bold text-sm rounded-xl shadow-lg shadow-violet-950/20 active:scale-95 transition-all flex items-center justify-center gap-2"
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
            Save Token PNG
          </button>
        </div>
      </div>

      <!-- Canvas Resolution -->
      <div class="p-5 border-b border-[#1f232e]">
        <h3 class="text-xs font-bold uppercase tracking-wider text-slate-500 font-outfit mb-3">
          Resolution & Bounds
        </h3>
        <div class="flex items-center gap-2">
          {#each sizeOptions as opt (opt)}
            <button
              on:click={() => (size = opt)}
              class="flex-1 py-1.5 text-xs font-mono font-bold rounded-lg border transition-all
                {size === opt
                ? 'bg-violet-600/20 border-violet-500 text-violet-300'
                : 'bg-[#171a22] border-[#2c3340] text-slate-400 hover:text-slate-200'}"
            >
              {opt}px
            </button>
          {/each}
        </div>
      </div>

      <!-- Image Transform adjustments -->
      <div class="p-5 border-b border-[#1f232e] flex flex-col gap-4">
        <div class="flex justify-between items-center">
          <h3 class="text-xs font-bold uppercase tracking-wider text-slate-500 font-outfit">
            Position & Zoom
          </h3>
          <button
            on:click={resetAll}
            class="text-[10px] text-violet-400 hover:text-violet-300 font-bold uppercase tracking-wider"
            >Reset</button
          >
        </div>

        <div class="flex flex-col gap-3">
          <!-- Zoom -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Zoom</span>
              <span class="font-mono text-violet-400 font-semibold">{Math.round(zoom * 100)}%</span>
            </div>
            <input
              type="range"
              bind:value={zoom}
              min="0.1"
              max="3"
              step="0.01"
              class="w-full accent-violet-500 bg-[#171a22] rounded-lg appearance-none h-1"
            />
          </div>

          <!-- Rotation -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Rotation</span>
              <span class="font-mono text-violet-400 font-semibold">{rotation}°</span>
            </div>
            <input
              type="range"
              bind:value={rotation}
              min="-180"
              max="180"
              step="1"
              class="w-full accent-violet-500 bg-[#171a22] rounded-lg appearance-none h-1"
            />
          </div>
        </div>
      </div>

      <!-- Background styling -->
      <div class="p-5 border-b border-[#1f232e] flex flex-col gap-3.5">
        <h3 class="text-xs font-bold uppercase tracking-wider text-slate-500 font-outfit">
          Background Color
        </h3>

        <!-- Preset quick colors -->
        <div class="grid grid-cols-6 gap-2">
          {#each presetColors as pc (pc.hex)}
            <button
              on:click={() => (bgColor = pc.hex)}
              title={pc.name}
              class="aspect-square rounded-lg border border-[#2d3440] hover:border-violet-500 shadow transition-all relative overflow-hidden flex items-center justify-center"
              style="background-color: {pc.hex}"
            >
              {#if pc.hex === '#00000000'}
                <!-- Checkered fallback cue -->
                <div
                  class="absolute inset-0 bg-[radial-gradient(#ffffff20_1px,transparent_1px)] [background-size:4px_4px]"
                ></div>
              {/if}
              {#if bgColor === pc.hex}
                <div class="w-1.5 h-1.5 rounded-full bg-white shadow-md"></div>
              {/if}
            </button>
          {/each}
        </div>

        <div class="flex items-center gap-3">
          <input
            type="color"
            bind:value={bgColor}
            class="w-8 h-8 rounded border border-[#2e3440] bg-transparent cursor-pointer"
          />
          <span class="text-xs font-mono font-bold text-slate-400">{bgColor}</span>
        </div>
      </div>

      <!-- Image Filters -->
      <div class="p-5 flex flex-col gap-4 flex-1">
        <h3 class="text-xs font-bold uppercase tracking-wider text-slate-500 font-outfit">
          Filters & Masking
        </h3>

        <div class="flex flex-col gap-3.5">
          <!-- Clip Portrait -->
          <label
            class="flex items-center justify-between text-xs text-slate-400 font-semibold cursor-pointer"
          >
            <span>Clip portrait inside frame mask</span>
            <input
              type="checkbox"
              bind:checked={clipPortrait}
              class="accent-violet-500 w-4 h-4 rounded"
            />
          </label>

          <!-- Transparency -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Portrait Opacity</span>
              <span class="font-mono text-violet-400 font-semibold"
                >{Math.round(transparency * 100)}%</span
              >
            </div>
            <input
              type="range"
              bind:value={transparency}
              min="0"
              max="1"
              step="0.01"
              class="w-full accent-violet-500 bg-[#171a22] rounded-lg appearance-none h-1"
            />
          </div>

          <!-- Blur -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Blur</span>
              <span class="font-mono text-violet-400 font-semibold">{blur}px</span>
            </div>
            <input
              type="range"
              bind:value={blur}
              min="0"
              max="10"
              step="0.5"
              class="w-full accent-violet-500 bg-[#171a22] rounded-lg appearance-none h-1"
            />
          </div>

          <!-- Glow (Brightness) -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Brighten & Glow</span>
              <span class="font-mono text-violet-400 font-semibold">+{glow}</span>
            </div>
            <input
              type="range"
              bind:value={glow}
              min="0"
              max="10"
              step="1"
              class="w-full accent-violet-500 bg-[#171a22] rounded-lg appearance-none h-1"
            />
          </div>

          <!-- Overlay opacity -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Frame Border Opacity</span>
              <span class="font-mono text-violet-400 font-semibold"
                >{Math.round(overlayOpacity * 100)}%</span
              >
            </div>
            <input
              type="range"
              bind:value={overlayOpacity}
              min="0"
              max="1"
              step="0.01"
              class="w-full accent-violet-500 bg-[#171a22] rounded-lg appearance-none h-1"
            />
          </div>
        </div>
      </div>
    </aside>
  </div>
</main>

<!-- PDF extractor Overlay modal -->
<PdfExtractorModal bind:show={showPdfExtractor} on:selectPortrait={handleSelectPdfPortrait} />

<style>
  main {
    height: 100vh;
  }

  .overlay-thumb-card {
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: hidden;
  }

  .overlay-thumb-card:hover {
    transform: translateY(-2px);
  }

  .thumb-container {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 0;
  }

  .thumb-image {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    transition: transform 0.25s;
  }

  .overlay-thumb-card:hover .thumb-image {
    transform: scale(1.05);
  }

  .thumb-label {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    font-size: 10px;
    font-weight: 700;
    line-height: 1.2;
    text-align: center;
    color: #f1f5f9;
    text-shadow:
      -1px -1px 0 #000,
      1px -1px 0 #000,
      -1px 1px 0 #000,
      1px 1px 0 #000,
      0px 2px 4px rgba(0, 0, 0, 0.9);
    pointer-events: none;
    width: 80%;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-overflow: ellipsis;
    word-break: break-word;
  }

  /* Premium inputs styles */
  input[type='range'] {
    -webkit-appearance: none;
  }

  input[type='range']::-webkit-slider-thumb {
    -webkit-appearance: none;
    height: 12px;
    width: 12px;
    border-radius: 50%;
    background: #8b5cf6;
    cursor: pointer;
    box-shadow: 0 0 8px rgba(139, 92, 246, 0.5);
  }

  input[type='range']::-webkit-slider-thumb:hover {
    background: #a78bfa;
    transform: scale(1.15);
  }
</style>
