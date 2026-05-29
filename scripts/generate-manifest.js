import fs from 'fs';
import path from 'path';
import { readPsd, initializeCanvas } from 'ag-psd';
import { createCanvas } from 'canvas';

// Initialize canvas factory for ag-psd to extract composite thumbnails in Node
initializeCanvas((width, height) => {
  return createCanvas(width, height);
});

const overlaysDir = path.join(process.cwd(), 'public', 'overlays');
const manifestPath = path.join(process.cwd(), 'src', 'lib', 'overlayManifest.json');

const categories = {};

function walk(dir, currentCategory) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    const stat = fs.statSync(fullPath);

    if (stat.isDirectory()) {
      const newCategory = currentCategory ? `${currentCategory}/${file}` : file;
      walk(fullPath, newCategory);
    } else {
      const ext = path.extname(file).toLowerCase();

      // Skip our generated thumbnails so they don't become duplicate presets
      if (file.endsWith('_thumb.png')) {
        continue;
      }

      if (['.png', '.jpg', '.jpeg', '.webp', '.psd'].includes(ext)) {
        const categoryKey = currentCategory || 'General';
        if (!categories[categoryKey]) {
          categories[categoryKey] = [];
        }

        // Normalize slashes for web URLs
        let relativePath = `/overlays/${currentCategory}/${file}`.replace(/\\/g, '/');
        // If currentCategory is empty (files in root of overlays/)
        if (!currentCategory) {
          relativePath = `/overlays/${file}`.replace(/\\/g, '/');
        }

        let thumbPath = relativePath;
        if (ext === '.psd') {
          // Generate a thumbnail PNG next to the PSD if it doesn't exist
          const thumbFullPath = fullPath.replace('.psd', '_thumb.png');
          thumbPath = relativePath.replace('.psd', '_thumb.png');
          if (!fs.existsSync(thumbFullPath) || fs.statSync(thumbFullPath).size < 1000) {
            try {
              console.log(`Generating thumbnail for ${file}...`);
              const buf = fs.readFileSync(fullPath);
              // Read without skipping layer data so we can extract the ring
              const psd = readPsd(buf, { skipThumbnail: true });

              // We want to extract the overlay ring (children[1] or fallback to canvas)
              const overlaySource =
                psd.children && psd.children.length > 1 && psd.children[1].canvas
                  ? psd.children[1].canvas
                  : psd.canvas;

              if (overlaySource) {
                // Resize thumbnail to max 128x128 for performance
                const MAX = 128;
                let w = overlaySource.width || psd.width;
                let h = overlaySource.height || psd.height;
                if (w > MAX || h > MAX) {
                  const scale = Math.min(MAX / w, MAX / h);
                  w = Math.floor(w * scale);
                  h = Math.floor(h * scale);
                }
                const thumbCanvas = createCanvas(w, h);
                const ctx = thumbCanvas.getContext('2d');
                ctx.drawImage(overlaySource, 0, 0, w, h);
                fs.writeFileSync(thumbFullPath, thumbCanvas.toBuffer('image/png'));
              }
            } catch (err) {
              console.error(`Failed to generate thumbnail for ${file}:`, err.message);
            }
          }
        }

        categories[categoryKey].push({
          name: path.basename(file, path.extname(file)),
          path: relativePath,
          thumbPath: thumbPath,
          type: ext.replace('.', '')
        });
      }
    }
  }
}

if (fs.existsSync(overlaysDir)) {
  walk(overlaysDir, '');
  fs.writeFileSync(manifestPath, JSON.stringify({ categories }, null, 2));
  console.log(
    `Manifest generated at ${manifestPath} with ${Object.keys(categories).length} categories.`
  );
} else {
  console.log('No public/overlays directory found. Manifest not generated.');
}
