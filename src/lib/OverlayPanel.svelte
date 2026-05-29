<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import manifest from './overlayManifest.json';
  import { parseTokenPsd } from './psdParser';
  import { getBasename, logError } from './utils';

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

  const dispatch = createEventDispatcher<{
    overlayChange: { maskUrl: string | null; overlayUrl: string };
  }>();

  // Parse Categories from Manifest
  const overlayCategories = Object.keys(manifest.categories);
  let selectedCategory =
    overlayCategories.find((c) => c.includes('Round/Smooth')) || overlayCategories[0] || '';
  $: currentCategoryOverlays = manifest.categories[selectedCategory] || [];

  // Custom Overlays loaded from system
  let customOverlays: CustomOverlay[] = [];
  let activeOverlayId = '';

  // UI state
  let loadingPsd = false;
  let errorMessage = '';

  // Select the default overlay on initialization
  export function selectDefaultOverlay() {
    if (currentCategoryOverlays.length > 0) {
      handleSelectPreset(currentCategoryOverlays[0]);
    }
  }

  // Helper to commit a custom overlay to the state and select it
  function commitCustomOverlay(name: string, mask: string | null, overlay: string) {
    // Check for duplicates and clean them up immediately to prevent leaks (N-4)
    const existingIndex = customOverlays.findIndex((c) => c.name === name);
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

    activeOverlayId = 'custom-0';
    dispatch('overlayChange', { maskUrl: mask, overlayUrl: overlay });
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
        logError('Failed to load custom overlay:', err);
        errorMessage =
          err instanceof Error ? err.message : 'Failed to read or parse the overlay file.';
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
            errorMessage =
              err instanceof Error ? err.message : 'Failed to read browser overlay file.';
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
          dispatch('overlayChange', { maskUrl: extracted.mask, overlayUrl: extracted.overlay });
        } else {
          errorMessage = 'The built-in PSD is missing expected Mask/Overlay layers.';
        }
      } catch (err: unknown) {
        errorMessage = `Failed to parse built-in PSD: ${err instanceof Error ? err.message : String(err)}`;
        logError(err);
      } finally {
        loadingPsd = false;
      }
    } else {
      dispatch('overlayChange', { maskUrl: null, overlayUrl: preset.path });
    }
  }

  function handleSelectCustom(custom: CustomOverlay, index: number) {
    activeOverlayId = `custom-${index}`;
    dispatch('overlayChange', { maskUrl: custom.mask, overlayUrl: custom.overlay });
  }

  /** Revoke all custom overlay blob URLs — called by parent onDestroy */
  export function revokeAllBlobUrls() {
    for (const custom of customOverlays) {
      if (custom.overlay && custom.overlay.startsWith('blob:')) {
        URL.revokeObjectURL(custom.overlay);
      }
      if (custom.mask && custom.mask.startsWith('blob:')) {
        URL.revokeObjectURL(custom.mask);
      }
    }
  }
</script>

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
              aria-label="Select custom overlay {custom.name}"
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
            aria-label="Select preset overlay {preset.name}"
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

<style>
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
</style>
