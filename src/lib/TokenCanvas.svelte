<script lang="ts">
  import { onMount, createEventDispatcher } from 'svelte';
  import { startDrag } from '@crabnebula/tauri-plugin-drag';
  import { writeFile, remove } from '@tauri-apps/plugin-fs';
  import { tempDir, join } from '@tauri-apps/api/path';

  // Component Props
  export let portraitUrl: string | null = null;
  export let maskUrl: string | null = null;
  export let overlayUrl: string | null = null;
  export let size: number = 256; // Output dimensions (e.g., 256x256, 512x512)
  export let bgColor: string = '#00000000'; // RGBA or HEX hex color
  export let zoom: number = 1.0;
  export let rotation: number = 0; // Degrees
  export let transparency: number = 1.0; // Portrait opacity
  export let blur: number = 0; // Filter blur (px)
  export let glow: number = 0; // Filter brightness/contrast (px)
  export let overlayOpacity: number = 1.0;
  export let clipPortrait: boolean = true;

  const dispatch = createEventDispatcher();

  // Canvas Refs
  let screenCanvas: HTMLCanvasElement;
  let screenCtx: CanvasRenderingContext2D | null = null;

  // Offscreen canvas for absolute composite compilation
  let offscreenCanvas: HTMLCanvasElement;
  let offscreenCtx: CanvasRenderingContext2D | null = null;

  // Render images
  let portraitImg: HTMLImageElement | null = null;
  let maskImg: HTMLImageElement | null = null;
  let overlayImg: HTMLImageElement | null = null;

  // Transformation states (relative to center of token canvas)
  let panX = 0;
  let panY = 0;
  let isDragging = false;
  let startDragX = 0;
  let startDragY = 0;

  // Pre-load default overlays/masks on mount
  onMount(() => {
    screenCtx = screenCanvas.getContext('2d');
    
    offscreenCanvas = document.createElement('canvas');
    offscreenCtx = offscreenCanvas.getContext('2d');
    
    updateCanvasSize();
    redraw();
  });

  // Reactive updates
  $: if (size) {
    updateCanvasSize();
  }

  $: if (
    portraitUrl || maskUrl || overlayUrl ||
    size || bgColor || zoom || rotation || 
    transparency || blur || glow || overlayOpacity || clipPortrait ||
    panX || panY
  ) {
    loadAndRedraw();
  }

  function updateCanvasSize() {
    if (screenCanvas && offscreenCanvas) {
      screenCanvas.width = size;
      screenCanvas.height = size;
      offscreenCanvas.width = size;
      offscreenCanvas.height = size;
      redraw();
    }
  }

  function loadImg(url: string | null): Promise<HTMLImageElement | null> {
    return new Promise((resolve) => {
      if (!url) {
        resolve(null);
        return;
      }
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => resolve(null);
      img.src = url;
    });
  }

  let loading = false;
  async function loadAndRedraw() {
    if (loading) return;
    loading = true;
    
    try {
      // Re-load images only if URLs changed
      if (portraitUrl && (!portraitImg || portraitImg.src !== portraitUrl)) {
        portraitImg = await loadImg(portraitUrl);
        // Reset pan on loading new portrait
        panX = 0;
        panY = 0;
      } else if (!portraitUrl) {
        portraitImg = null;
      }

      if (maskUrl && (!maskImg || maskImg.src !== maskUrl)) {
        maskImg = await loadImg(maskUrl);
      } else if (!maskUrl) {
        maskImg = null;
      }

      if (overlayUrl && (!overlayImg || overlayImg.src !== overlayUrl)) {
        overlayImg = await loadImg(overlayUrl);
      } else if (!overlayUrl) {
        overlayImg = null;
      }
      
      redraw();
    } catch (e) {
      console.error(e);
    } finally {
      loading = false;
    }
  }

  function redraw() {
    if (!offscreenCtx || !screenCtx) return;

    const ctx = offscreenCtx;
    const w = size;
    const h = size;

    // 1. Clear Canvas
    ctx.clearRect(0, 0, w, h);

    // 2. Draw Masked Content (Background + Portrait)
    if (clipPortrait && maskImg) {
      // Create a temporary canvas to composite the portrait and background, then clip it
      const tempCanvas = document.createElement('canvas');
      tempCanvas.width = w;
      tempCanvas.height = h;
      const tempCtx = tempCanvas.getContext('2d')!;

      // a. Draw Background Fill
      tempCtx.fillStyle = bgColor;
      tempCtx.fillRect(0, 0, w, h);

      // b. Draw Portrait with transforms
      if (portraitImg) {
        tempCtx.save();
        tempCtx.translate(w / 2 + panX, h / 2 + panY);
        tempCtx.rotate((rotation * Math.PI) / 180);

        const aspect = portraitImg.width / portraitImg.height;
        let drawW = w * zoom;
        let drawH = h * zoom;
        if (aspect > 1) {
          drawW = drawH * aspect;
        } else {
          drawH = drawW / aspect;
        }

        let filters: string[] = [];
        if (blur > 0) filters.push(`blur(${blur}px)`);
        if (glow > 0) filters.push(`brightness(${1 + glow / 10}) contrast(${1 + glow / 20})`);
        if (filters.length > 0) tempCtx.filter = filters.join(' ');

        tempCtx.globalAlpha = transparency;
        tempCtx.drawImage(portraitImg, -drawW / 2, -drawH / 2, drawW, drawH);
        tempCtx.restore();
      }

      // c. Clip everything using destination-out
      // (The mask layer is opaque on the OUTSIDE. destination-out erases the background/portrait where the mask is opaque)
      tempCtx.globalCompositeOperation = 'destination-out';
      tempCtx.drawImage(maskImg, 0, 0, w, h);

      // d. Draw the perfectly masked result onto the main canvas
      ctx.drawImage(tempCanvas, 0, 0);
      
    } else {
      // No mask — draw background and portrait directly
      ctx.fillStyle = bgColor;
      ctx.fillRect(0, 0, w, h);

      if (portraitImg) {
        ctx.save();
        ctx.translate(w / 2 + panX, h / 2 + panY);
        ctx.rotate((rotation * Math.PI) / 180);

        const aspect = portraitImg.width / portraitImg.height;
        let drawW = w * zoom;
        let drawH = h * zoom;
        if (aspect > 1) {
          drawW = drawH * aspect;
        } else {
          drawH = drawW / aspect;
        }

        let filters: string[] = [];
        if (blur > 0) filters.push(`blur(${blur}px)`);
        if (glow > 0) filters.push(`brightness(${1 + glow / 10}) contrast(${1 + glow / 20})`);
        if (filters.length > 0) ctx.filter = filters.join(' ');

        ctx.globalAlpha = transparency;
        ctx.drawImage(portraitImg, -drawW / 2, -drawH / 2, drawW, drawH);
        ctx.restore();
      }
    }

    // 4. Draw Overlay Frame
    if (overlayImg) {
      ctx.save();
      ctx.globalAlpha = overlayOpacity;
      ctx.drawImage(overlayImg, 0, 0, w, h);
      ctx.restore();
    }

    // 5. Transfer to screen
    screenCtx.clearRect(0, 0, w, h);
    screenCtx.drawImage(offscreenCanvas, 0, 0);
  }

  // Mouse / Touch Interaction for Panning
  function handleMouseDown(e: MouseEvent) {
    if (!portraitImg) return;
    isDragging = true;
    startDragX = e.clientX;
    startDragY = e.clientY;
    screenCanvas.style.cursor = 'grabbing';
  }

  function handleMouseMove(e: MouseEvent) {
    if (!isDragging || !portraitImg) return;
    const deltaX = e.clientX - startDragX;
    const deltaY = e.clientY - startDragY;
    
    panX += deltaX;
    panY += deltaY;
    
    startDragX = e.clientX;
    startDragY = e.clientY;
    
    redraw();
  }

  function handleMouseUp() {
    isDragging = false;
    if (screenCanvas) {
      screenCanvas.style.cursor = portraitImg ? 'grab' : 'default';
    }
  }

  function handleWheel(e: WheelEvent) {
    if (!portraitImg) return;
    e.preventDefault();
    const zoomStep = 0.05;
    if (e.deltaY < 0) {
      // Zoom in
      zoom = Math.min(zoom + zoomStep, 5.0);
    } else {
      // Zoom out
      zoom = Math.max(zoom - zoomStep, 0.1);
    }
    dispatch('zoomChange', { zoom });
  }

  // Handle Drag-and-Drop Files directly into Canvas
  function handleDragOver(e: DragEvent) {
    e.preventDefault();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy';
    }
  }

  async function handleDrop(e: DragEvent) {
    e.preventDefault();
    if (!e.dataTransfer) return;

    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) {
      const file = files[0];
      if (file.type.startsWith('image/')) {
        const reader = new FileReader();
        reader.onload = () => {
          portraitUrl = reader.result as string;
          dispatch('portraitLoaded', { url: portraitUrl });
        };
        reader.readAsDataURL(file);
      }
    }
  }

  let isTauri = typeof window !== 'undefined' && (window as any).__TAURI__ !== undefined;

  // Support Native OS Drag Out for Tauri environment (fixes macOS Tahoe drag-out)
  async function handleDragStartTauri(e: MouseEvent) {
    if (!isTauri) return;
    if (e.button !== 0) return; // Only drag with left mouse button
    if (!screenCanvas) return;

    e.preventDefault();

    try {
      const dataUrl = offscreenCanvas.toDataURL('image/png');
      const parts = dataUrl.split(',');
      if (parts.length < 2) {
        console.error("Malformed canvas data URL");
        return;
      }
      const base64Data = parts[1];
      const binaryString = atob(base64Data);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      const tempPath = await tempDir();
      const filename = 'tokentool-drag-token.png';
      const absolutePath = await join(tempPath, filename);

      await writeFile(absolutePath, bytes);

      try {
        await startDrag({
          item: [absolutePath],
          icon: absolutePath
        });
      } finally {
        // Always clean up the temporary file after drag concludes
        try {
          await remove(absolutePath);
        } catch (cleanupErr) {
          console.warn('Failed to clean up temp drag file:', cleanupErr);
        }
      }
    } catch (err) {
      console.error('Failed to trigger native drag-out:', err);
    }
  }

  // Support HTML5 Drag Out fallback for Browser environment (Chrome-only)
  function handleDragStartBrowser(e: DragEvent) {
    if (isTauri) return;
    if (!screenCanvas) return;
    
    const dataUrl = offscreenCanvas.toDataURL('image/png');
    const filename = 'tokentool-token.png';
    const downloadData = `image/png:${filename}:${dataUrl}`;
    
    if (e.dataTransfer) {
      e.dataTransfer.setData('DownloadURL', downloadData);
      e.dataTransfer.effectAllowed = 'copy';
      
      // Visual drag cue
      const dragIcon = document.createElement('img');
      dragIcon.src = dataUrl;
      dragIcon.width = 64;
      dragIcon.height = 64;
      dragIcon.style.borderRadius = '50%';
      document.body.appendChild(dragIcon);
      e.dataTransfer.setDragImage(dragIcon, 32, 32);
      
      // Cleanup drag icon
      setTimeout(() => {
        document.body.removeChild(dragIcon);
      }, 0);
    }
  }

  // Public methods accessible from outside
  export function exportPng(): string {
    return offscreenCanvas.toDataURL('image/png');
  }

  export function resetPosition() {
    panX = 0;
    panY = 0;
    zoom = 1.0;
    rotation = 0;
    redraw();
  }
</script>

<div class="canvas-container select-none">
  <canvas
    bind:this={screenCanvas}
    on:mousedown={handleMouseDown}
    on:mousemove={handleMouseMove}
    on:mouseup={handleMouseUp}
    on:mouseleave={handleMouseUp}
    on:wheel={handleWheel}
    on:dragover={handleDragOver}
    on:drop={handleDrop}
    draggable="false"
    class="interactive-canvas shadow-2xl transition-all duration-300"
    style="width: {size}px; height: {size}px; cursor: {portraitImg ? 'grab' : 'default'}"
  ></canvas>
  
  {#if !portraitUrl}
    <div class="canvas-overlay pointer-events-none absolute flex flex-col items-center justify-center text-center p-6 text-gray-500 font-medium">
      <svg class="w-8 h-8 mb-2 text-violet-500 opacity-60 animate-bounce" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path>
      </svg>
      <p class="text-sm font-semibold text-slate-300">Drag & Drop Portrait Here</p>
      <p class="text-xs text-slate-500 mt-1">Supports PNG, JPG, WebP</p>
    </div>
  {:else}
    <!-- Dedicated Premium Drag-Out Hand Handle in bottom-right corner -->
    <button 
      draggable={!isTauri}
      on:dragstart={handleDragStartBrowser}
      on:mousedown={handleDragStartTauri}
      class="drag-handle-badge select-none"
      type="button"
      title="Drag this hand to export your completed token directly to desktop/Discord!"
    >
      <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 11.5V14m0-2.5v-6a1.5 1.5 0 113 0V12m0-6.5v-1a1.5 1.5 0 113 0V12m0-7.5v-1a1.5 1.5 0 113 0V12m0-8.5a1.5 1.5 0 113 0v11.5a5.5 5.5 0 01-11 0V12a1.5 1.5 0 113 0"></path>
      </svg>
    </button>
  {/if}
</div>

<style>
  .canvas-container {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #1a1e27;
    border: 2px dashed #3f4756;
    border-radius: 1.5rem;
    overflow: hidden;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.4);
  }

  .canvas-container:hover {
    border-color: #7c3aed;
    background-color: #1d222d;
    box-shadow: 0 0 20px rgba(124, 58, 237, 0.2);
  }

  .interactive-canvas {
    border-radius: 1.25rem;
    display: block;
    user-select: none;
    touch-action: none;
  }

  .canvas-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #94a3b8;
  }

  .drag-handle-badge {
    position: absolute;
    bottom: 12px;
    right: 12px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background-color: rgba(124, 58, 237, 0.8);
    border: 1px solid rgba(255, 255, 255, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: grab;
    transition: all 0.2s ease-in-out;
    z-index: 20;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
    backdrop-filter: blur(4px);
    padding: 0;
  }

  .drag-handle-badge:hover {
    transform: scale(1.1);
    background-color: #8b5cf6;
    box-shadow: 0 4px 15px rgba(124, 58, 237, 0.5);
  }

  .drag-handle-badge:active {
    cursor: grabbing;
  }
</style>
