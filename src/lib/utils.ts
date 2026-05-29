/**
 * Robust environment detector for Tauri applications
 */
export const isTauri = typeof window !== 'undefined' && (window as unknown as Record<string, unknown>).__TAURI__ !== undefined;

/**
 * Converts a base64 image data string (with or without "data:image/..." header) to a Uint8Array
 */
export function base64ToUint8Array(base64Str: string): Uint8Array {
  const binaryString = atob(base64Str);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
}

/**
 * Converts a full Data URL (e.g. "data:image/png;base64,...") to a Uint8Array.
 * Returns null if the URL is malformed.
 */
export function dataUrlToUint8Array(dataUrl: string): Uint8Array | null {
  const parts = dataUrl.split(',');
  if (parts.length < 2) {
    console.error('Malformed data URL');
    return null;
  }
  return base64ToUint8Array(parts[1]);
}

/**
 * Robust helper to extract the base file name from a filesystem path
 */
export function getBasename(path: string): string {
  return path.split('\\').pop()?.split('/').pop() || '';
}

/**
 * Safe console error logger that only executes in development mode
 */
export function logError(...args: unknown[]): void {
  if (import.meta.env.DEV) {
    console.error(...args);
  }
}

/**
 * Safe console warning logger that only executes in development mode
 */
export function logWarn(...args: unknown[]): void {
  if (import.meta.env.DEV) {
    console.warn(...args);
  }
}
