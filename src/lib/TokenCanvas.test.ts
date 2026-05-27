import { describe, it, expect } from 'vitest';

describe('HTML5 Canvas Layer Masking Pipeline', () => {
  it('prevents persistent globalCompositeOperation state leaks across multiple redraws', () => {
    // 1. Create the persistent canvases and contexts (simulating Svelte's onMount lifecycle)
    const offscreenCanvas = document.createElement('canvas');
    offscreenCanvas.width = 256;
    offscreenCanvas.height = 256;
    const offscreenCtx = offscreenCanvas.getContext('2d')!;

    const tempCanvas = document.createElement('canvas');
    tempCanvas.width = 256;
    tempCanvas.height = 256;
    const tempCtx = tempCanvas.getContext('2d')!;

    // Create 1x1 canvases to act as valid, decoded image sources
    const mockPortrait = document.createElement('canvas');
    mockPortrait.width = 1;
    mockPortrait.height = 1;

    const mockMask = document.createElement('canvas');
    mockMask.width = 1;
    mockMask.height = 1;

    // Simulate two consecutive redraw frames
    for (let frame = 1; frame <= 2; frame++) {
      // --- CRITICAL FIX: Reset composite operation to default before clearing and rendering ---
      tempCtx.globalCompositeOperation = 'source-over';
      tempCtx.clearRect(0, 0, 256, 256);

      // a. Draw Background Fill
      tempCtx.fillStyle = '#ff0000'; // Red
      tempCtx.fillRect(0, 0, 256, 256);

      // b. Draw Portrait
      tempCtx.drawImage(mockPortrait, 0, 0, 256, 256);

      // c. Clip using destination-out (masking)
      tempCtx.globalCompositeOperation = 'destination-out';
      tempCtx.drawImage(mockMask, 0, 0, 256, 256);

      // d. Draw composite result onto the main offscreen canvas
      offscreenCtx.clearRect(0, 0, 256, 256);
      offscreenCtx.drawImage(tempCanvas, 0, 0);
    }

    // Extract pixel data from the canvas center
    const pixel = offscreenCtx.getImageData(128, 128, 1, 1).data;

    // If the globalCompositeOperation state leak is present, tempCtx.globalCompositeOperation
    // remains 'destination-out' on the second frame, making fillRect act as an eraser
    // and leaving the canvas completely transparent [0, 0, 0, 0].
    // With the fix, the background remains red [255, 0, 0, 255].
    const isTransparent = pixel[0] === 0 && pixel[1] === 0 && pixel[2] === 0 && pixel[3] === 0;
    
    expect(isTransparent).toBe(false);
    expect(pixel[0]).toBe(255); // Red channel should be fully opaque
    expect(pixel[3]).toBe(255); // Alpha channel must be fully opaque
  });
});
