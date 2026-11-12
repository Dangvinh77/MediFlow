// Regenerates the MediFlow launcher icons for Android, iOS, web and Windows from the brand mark
// in frontend/public/brand/mediflow-icon.svg. Run after `pnpm install` in frontend/ (it borrows
// that project's sharp):  node mobile/tool/generate_app_icons.mjs
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const mobile = join(dirname(fileURLToPath(import.meta.url)), '..');
// sharp is a dependency of next, so resolve it from there rather than adding a mobile dependency.
const frontendRequire = createRequire(join(mobile, '..', 'frontend', 'package.json'));
const sharp = createRequire(frontendRequire.resolve('next/package.json'))('sharp');

const BACKGROUND = '#083F45';
// The cross and ECG trace from the brand mark, drawn in a 100x100 box.
const MARK = `
  <path fill="#FFFFFF" d="M42 0H58a9 9 0 0 1 9 9V33H91a9 9 0 0 1 9 9V58a9 9 0 0 1-9 9H67V91a9 9 0 0 1-9 9H42a9 9 0 0 1-9-9V67H9a9 9 0 0 1-9-9V42a9 9 0 0 1 9-9H33V9a9 9 0 0 1 9-9Z"/>
  <path fill="none" stroke="#0F766E" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" d="M10 50H36L44 38L54 62L62 50H90"/>`;

// markSize is the mark's share of the canvas; radius 0 gives a full-bleed square.
function svg({ markSize, radius = 0, background = BACKGROUND }) {
  const scale = markSize;
  const offset = (1 - markSize) * 50;
  const bg = background ? `<rect width="100" height="100" rx="${radius}" fill="${background}"/>` : '';
  return Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">${bg}
    <g transform="translate(${offset} ${offset}) scale(${scale})">${MARK}</g></svg>`);
}

async function png(source, size, file, { flatten = false } = {}) {
  mkdirSync(dirname(file), { recursive: true });
  let image = sharp(source, { density: Math.max(72, Math.ceil((size / 100) * 72 * 4)) }).resize(size, size);
  if (flatten) image = image.flatten({ background: BACKGROUND });
  await image.png({ compressionLevel: 9 }).toFile(file);
}

// Legacy launchers keep the brand's rounded square; iOS and maskable icons must be full bleed
// because the OS applies its own mask (and the App Store rejects transparency).
const rounded = svg({ markSize: 0.66, radius: 22 });
const square = svg({ markSize: 0.66 });
// Adaptive foregrounds are 108dp with a 66dp (0.61) safe zone; the mark stays just inside it.
const foreground = svg({ markSize: 0.58, background: null });

const res = join(mobile, 'android', 'app', 'src', 'main', 'res');
const densities = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
for (const [name, factor] of Object.entries(densities)) {
  await png(rounded, 48 * factor, join(res, `mipmap-${name}`, 'ic_launcher.png'));
  await png(foreground, 108 * factor, join(res, `mipmap-${name}`, 'ic_launcher_foreground.png'));
}
mkdirSync(join(res, 'mipmap-anydpi-v26'), { recursive: true });
writeFileSync(join(res, 'mipmap-anydpi-v26', 'ic_launcher.xml'), `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
`);
writeFileSync(join(res, 'values', 'ic_launcher_background.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">${BACKGROUND}</color>
</resources>
`);

const ios = join(mobile, 'ios', 'Runner', 'Assets.xcassets', 'AppIcon.appiconset');
for (const [points, scales] of [[20, [1, 2, 3]], [29, [1, 2, 3]], [40, [1, 2, 3]], [60, [2, 3]],
  [76, [1, 2]], [83.5, [2]], [1024, [1]]]) {
  for (const scale of scales) {
    await png(square, Math.round(points * scale), join(ios, `Icon-App-${points}x${points}@${scale}x.png`), { flatten: true });
  }
}

const web = join(mobile, 'web');
await png(rounded, 192, join(web, 'icons', 'Icon-192.png'));
await png(rounded, 512, join(web, 'icons', 'Icon-512.png'));
await png(svg({ markSize: 0.6 }), 192, join(web, 'icons', 'Icon-maskable-192.png'));
await png(svg({ markSize: 0.6 }), 512, join(web, 'icons', 'Icon-maskable-512.png'));
await png(rounded, 32, join(web, 'favicon.png'));

// Windows .ico: PNG-compressed entries (supported since Vista).
const icoSizes = [16, 24, 32, 48, 64, 128, 256];
const frames = await Promise.all(icoSizes.map((size) =>
  sharp(rounded, { density: 300 }).resize(size, size).png().toBuffer()));
const header = Buffer.alloc(6 + 16 * frames.length);
header.writeUInt16LE(0, 0);
header.writeUInt16LE(1, 2);
header.writeUInt16LE(frames.length, 4);
let offset = header.length;
frames.forEach((frame, index) => {
  const entry = 6 + 16 * index;
  const size = icoSizes[index];
  header.writeUInt8(size === 256 ? 0 : size, entry);
  header.writeUInt8(size === 256 ? 0 : size, entry + 1);
  header.writeUInt16LE(1, entry + 4);
  header.writeUInt16LE(32, entry + 6);
  header.writeUInt32LE(frame.length, entry + 8);
  header.writeUInt32LE(offset, entry + 12);
  offset += frame.length;
});
writeFileSync(join(mobile, 'windows', 'runner', 'resources', 'app_icon.ico'), Buffer.concat([header, ...frames]));

console.log('MediFlow app icons regenerated for Android, iOS, web and Windows.');
