<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import TokenCanvas from './lib/TokenCanvas.svelte';
  import OverlayPanel from './lib/OverlayPanel.svelte';
  import SettingsPanel from './lib/SettingsPanel.svelte';
  import PdfExtractorModal from './lib/PdfExtractorModal.svelte';
  import { logError } from './lib/utils';

  // State binds
  let portraitUrl: string | null = null;
  let maskUrl: string | null = null;
  let overlayUrl: string | null = null;

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
  let overlayPanelRef: OverlayPanel;
  let showPdfExtractor = false;
  let mounted = false;

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
        logError('Failed to parse cached settings', e);
      }
    }

    // Load first default overlay
    if (overlayPanelRef) {
      overlayPanelRef.selectDefaultOverlay();
    }
    mounted = true;
  });

  onDestroy(() => {
    // Revoke portraitUrl if it's a blob URL
    if (portraitUrl && portraitUrl.startsWith('blob:')) {
      URL.revokeObjectURL(portraitUrl);
    }
    // Revoke all custom overlay blob URLs via the OverlayPanel
    if (overlayPanelRef) {
      overlayPanelRef.revokeAllBlobUrls();
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

  function handleOverlayChange(e: CustomEvent<{ maskUrl: string | null; overlayUrl: string }>) {
    maskUrl = e.detail.maskUrl;
    overlayUrl = e.detail.overlayUrl;
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
            >Demo</span
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
    <OverlayPanel bind:this={overlayPanelRef} on:overlayChange={handleOverlayChange} />

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
          on:portraitDrop={(e) => portraitUrl = e.detail.url}
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
    <SettingsPanel
      bind:size
      bind:bgColor
      bind:zoom
      bind:rotation
      bind:transparency
      bind:blur
      bind:glow
      bind:overlayOpacity
      bind:clipPortrait
      bind:fileName
      bind:useFileNumbering
      bind:fileSuffix
      {canvasRef}
      on:resetAll={resetAll}
    />
  </div>
</main>

<!-- PDF extractor Overlay modal -->
<PdfExtractorModal bind:show={showPdfExtractor} on:selectPortrait={handleSelectPdfPortrait} />

<style>
  main {
    height: 100vh;
  }
</style>
