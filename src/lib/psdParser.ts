import { readPsd, initializeCanvas } from 'ag-psd';
import { logError } from './utils';

// Initialize canvas factory for ag-psd to decode layer pixels in the browser
initializeCanvas((width, height) => {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  return canvas;
});

export interface PsdLayers {
  mask: string | null; // Grayscale/transparency mask as base64/dataURL
  overlay: string | null; // Visible overlay border as base64/dataURL
  width: number;
  height: number;
}

/**
 * Parses a Photoshop PSD file and extracts Layer 1 (Mask) and Layer 2 (Overlay).
 * MapTool/TokenTool overlay format defines:
 * - Layer 0: The transparency mask (where non-transparent/colored pixels represent visible area)
 * - Layer 1: The visible border overlay frame
 *
 * @param fileBuffer ArrayBuffer containing the PSD file bytes
 * @returns Promise<PsdLayers>
 */
export async function parseTokenPsd(fileBuffer: ArrayBuffer): Promise<PsdLayers> {
  try {
    const psd = readPsd(fileBuffer, { skipThumbnail: true });
    const width = psd.width;
    const height = psd.height;

    let maskDataUrl: string | null = null;
    let overlayDataUrl: string | null = null;

    if (!psd.children || psd.children.length < 2) {
      if (psd.canvas) {
        overlayDataUrl = psd.canvas.toDataURL('image/png');
      }
      return { mask: null, overlay: overlayDataUrl, width, height };
    }

    const maskLayer = psd.children[0];
    const overlayLayer = psd.children[1];

    if (maskLayer && maskLayer.canvas) {
      maskDataUrl = maskLayer.canvas.toDataURL('image/png');
    }

    if (overlayLayer && overlayLayer.canvas) {
      overlayDataUrl = overlayLayer.canvas.toDataURL('image/png');
    } else if (psd.canvas) {
      overlayDataUrl = psd.canvas.toDataURL('image/png');
    }

    // Validate that we successfully decoded both mask and overlay layers
    if (!maskDataUrl || !overlayDataUrl) {
      throw new Error(
        `Layer decode failed! Mask: ${maskLayer?.name} (canvas: ${!!maskLayer?.canvas}). Overlay: ${overlayLayer?.name} (canvas: ${!!overlayLayer?.canvas}).`
      );
    }

    return {
      mask: maskDataUrl,
      overlay: overlayDataUrl,
      width,
      height
    };
  } catch (error: unknown) {
    logError('Failed to parse PSD file:', error);
    throw new Error(
      'Could not parse PSD format. Ensure it has Layer 1 (Mask) and Layer 2 (Overlay).',
      { cause: error }
    );
  }
}
