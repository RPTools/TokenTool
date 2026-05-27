import { describe, it, expect } from 'vitest';
import { base64ToUint8Array, dataUrlToUint8Array, getBasename } from './utils';

describe('utils', () => {
  describe('base64ToUint8Array', () => {
    it('converts base64 to Uint8Array', () => {
      // "Hello" in base64 is "SGVsbG8="
      const base64 = 'SGVsbG8=';
      const result = base64ToUint8Array(base64);
      const expected = new Uint8Array([72, 101, 108, 108, 111]); // "Hello"
      expect(result).toEqual(expected);
    });
  });

  describe('dataUrlToUint8Array', () => {
    it('converts data URL to Uint8Array', () => {
      const dataUrl = 'data:image/png;base64,SGVsbG8=';
      const result = dataUrlToUint8Array(dataUrl);
      const expected = new Uint8Array([72, 101, 108, 108, 111]);
      expect(result).toEqual(expected);
    });

    it('returns null for malformed data URL', () => {
      const malformedUrl = 'data:image/png;base64SGVsbG8='; // Missing comma
      const result = dataUrlToUint8Array(malformedUrl);
      expect(result).toBeNull();
    });
  });

  describe('getBasename', () => {
    it('extracts basename from Windows path', () => {
      expect(getBasename('C:\\Users\\matta\\Downloads\\image.png')).toBe('image.png');
    });

    it('extracts basename from Unix path', () => {
      expect(getBasename('/home/user/Downloads/image.png')).toBe('image.png');
    });

    it('handles mixed separators', () => {
      expect(getBasename('C:\\Users/matta\\Downloads/image.png')).toBe('image.png');
    });

    it('returns empty string for empty path', () => {
      expect(getBasename('')).toBe('');
    });

    it('returns filename when no separators exist', () => {
      expect(getBasename('image.png')).toBe('image.png');
    });
  });
});
