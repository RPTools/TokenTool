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

  describe('loadAndRedraw async state machine', () => {
    it('correctly catches up to the latest URLs on rapid concurrent changes without dropping frames', async () => {
      let loading = false;
      let portraitUrl = 'initial';
      let lastLoadedPortraitUrl: string | null = null;
      let drawCount = 0;

      // Mock loader with simulated latency
      async function mockLoadImg(url: string | null): Promise<string | null> {
        await new Promise((resolve) => setTimeout(resolve, 20));
        return url;
      }

      async function simulatedLoadAndRedraw() {
        if (loading) return;
        loading = true;

        const targetPortraitUrl = portraitUrl;

        try {
          if (targetPortraitUrl !== lastLoadedPortraitUrl) {
            await mockLoadImg(targetPortraitUrl);
            lastLoadedPortraitUrl = targetPortraitUrl;
          }
          drawCount++;
        } finally {
          loading = false;
          // If URLs changed while we were loading, re-trigger to catch up
          if (portraitUrl !== lastLoadedPortraitUrl) {
            await simulatedLoadAndRedraw();
          }
        }
      }

      // 1. Kick off the initial load
      const firstPromise = simulatedLoadAndRedraw();
      expect(loading).toBe(true);
      expect(lastLoadedPortraitUrl).toBeNull();

      // 2. While loading is active, change URLs rapidly
      portraitUrl = 'intermediate';
      await simulatedLoadAndRedraw(); // Immediately returns due to loading guard

      portraitUrl = 'latest';
      await simulatedLoadAndRedraw(); // Immediately returns due to loading guard

      // 3. Await first promise, which will trigger the catch-up in finally block
      await firstPromise;

      // Allow the catch-up microtasks to resolve fully
      await new Promise((resolve) => setTimeout(resolve, 50));

      // 4. Assertions
      expect(loading).toBe(false);
      expect(lastLoadedPortraitUrl).toBe('latest'); // Successfully caught up to the latest URL!
      expect(drawCount).toBe(2); // Frame 1: initial. Frame 2: latest. Intermediate frame bypassed.
    });
  });
});
