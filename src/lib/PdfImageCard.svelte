<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  export let imgUrl: string;
  export let index: number;
  export let selected: boolean = false;

  const dispatch = createEventDispatcher<{
    toggleSelect: { index: number };
    useAsPortrait: { url: string };
    saveImage: { url: string; index: number };
  }>();

  function handleDragStart(e: DragEvent) {
    if (e.dataTransfer) {
      e.dataTransfer.setData('text/plain', imgUrl);
      e.dataTransfer.setData('url', imgUrl);
      e.dataTransfer.effectAllowed = 'copy';
    }
  }
</script>

<div class="image-card" role="listitem">
  <!-- Checkbox Button in Top-Left Corner (Highly Reactive & Always on Top) -->
  <button
    type="button"
    class="select-checkbox-badge"
    class:checked={selected}
    on:click|stopPropagation={() => dispatch('toggleSelect', { index })}
    aria-label="Select Image {index + 1}"
  >
    {#if selected}
      <svg
        class="w-3 h-3 text-white"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="3"
          d="M5 13l4 4L19 7"
        ></path>
      </svg>
    {/if}
  </button>

  <!-- Thumbnail -->
  <div
    class="aspect-square bg-slate-900 overflow-hidden flex items-center justify-center"
  >
    <img
      src={imgUrl}
      alt="Extracted resource {index}"
      class="w-full h-full object-cover hover-scale cursor-grab active:cursor-grabbing"
      draggable="true"
      on:dragstart={handleDragStart}
    />
  </div>

  <!-- Hover Overlay actions (Slightly Layered Below Checkbox) -->
  <div class="hover-overlay">
    <button on:click={() => dispatch('useAsPortrait', { url: imgUrl })} class="use-portrait-button">
      Use as Portrait
    </button>
    <button
      on:click={() => dispatch('saveImage', { url: imgUrl, index })}
      class="save-image-button"
    >
      Save to File
    </button>
    <p class="text-[10px] text-center text-slate-500 mt-1">Drag directly to Canvas</p>
  </div>
</div>

<style>
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

  /* Custom Checkbox Badges on Thumbnails */
  .select-checkbox-badge {
    position: absolute;
    top: 10px;
    left: 10px;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    background-color: rgba(15, 17, 21, 0.65);
    border: 2px solid #3f4756;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.2s ease;
    z-index: 20; /* Keep checkbox above the hover overlay */
    backdrop-filter: blur(4px);
    padding: 0;
  }

  .select-checkbox-badge:hover {
    border-color: #8b5cf6;
    background-color: rgba(139, 92, 246, 0.35);
  }

  .select-checkbox-badge.checked {
    background-color: #7c3aed;
    border-color: #7c3aed;
    box-shadow: 0 0 8px rgba(124, 58, 237, 0.4);
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
    gap: 0.5rem;
    z-index: 10; /* Hover overlay is below the checkbox */
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

  .save-image-button {
    width: 100%;
    padding: 0.5rem 0;
    background-color: #1b1f28;
    border: 1px solid #2e3440;
    color: #cbd5e1;
    font-size: 0.75rem;
    font-weight: 600;
    border-radius: 0.5rem;
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .save-image-button:hover {
    background-color: #252a35;
    color: #ffffff;
    border-color: #434c5e;
  }
</style>
