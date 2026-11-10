// Renders scene.html to an MP4 frame by frame. Needs the playwright npm package and ffmpeg (with libx264) on PATH.
// Usage: node render.mjs [video [out.mp4] | stills [t1,t2,...]]. Set CHROMIUM_PATH to use a specific browser binary.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import path from 'node:path';

const dir = path.dirname(new URL(import.meta.url).pathname);
const [mode = 'video', outArg] = process.argv.slice(2);
const FPS = 30;

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const page = await browser.newPage({ viewport: { width: 1080, height: 1350 }, deviceScaleFactor: 1 });
await page.goto('file://' + path.join(dir, 'scene.html'));
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(500);
const total = await page.evaluate(() => window.TOTAL);

if (mode === 'stills') {
  for (const t of (outArg || '2,6,10.5,13.5,18.6,22,26,30').split(',').map(Number)) {
    await page.evaluate((t) => window.render(t), t);
    await page.screenshot({ path: path.join(dir, `still_${t}.png`) });
  }
} else {
  const out = outArg || path.join(dir, 'bowerbird.mp4');
  const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-preset', 'slow', '-crf', '18', '-movflags', '+faststart', out],
    { stdio: ['pipe', 'ignore', 'inherit'] });
  const n = Math.round(total * FPS);
  for (let i = 0; i < n; i++) {
    await page.evaluate((t) => window.render(t), i / FPS);
    const buf = await page.screenshot({ type: 'png' });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
  console.log('wrote', out, n, 'frames');
}
await browser.close();
