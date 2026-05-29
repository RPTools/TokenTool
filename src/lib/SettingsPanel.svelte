<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import type TokenCanvas from './TokenCanvas.svelte';
  import { dataUrlToUint8Array, logError } from './utils';

  const dispatch = createEventDispatcher<{
    resetAll: void;
  }>();

  // Canvas settings — bidirectionally bound from parent
  export let size: number = 256;
  export let bgColor: string = '#00000000';
  export let zoom: number = 1.0;
  export let rotation: number = 0;
  export let transparency: number = 1.0;
  export let blur: number = 0;
  export let glow: number = 0;
  export let overlayOpacity: number = 1.0;
  export let clipPortrait: boolean = true;

  // Export options
  export let fileName: string = 'token';
  export let useFileNumbering: boolean = true;
  export let fileSuffix: number = 1;

  // Canvas reference — read-only, used for exportPng()
  export let canvasRef: TokenCanvas | undefined = undefined;

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
          logError('Failed to decode token image data');
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
</script>

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
        on:click={() => dispatch('resetAll')}
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
          max="5"
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
          aria-label="Set background to {pc.name}"
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

<style>
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
