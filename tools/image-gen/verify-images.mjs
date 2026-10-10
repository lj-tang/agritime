import { readFile, stat } from 'node:fs/promises';
import { resolve } from 'node:path';

const OUT_DIR = resolve(import.meta.dirname, '..', '..', 'frontend', 'assets', 'img');

const PNG_MAGIC = 0x89504e47;
const KB = 1024;

const EXPECTED = [
  { file: 'common-logo.svg', max: 30 * KB },
  { file: 'common-logo.png', w: 96, h: 96, max: 30 * KB },
  { file: 'common-logo@2x.png', w: 192, h: 192, max: 30 * KB },
  { file: 'index-bg.svg', max: 200 * KB },
  { file: 'index-bg.png', w: 720, h: 1280, max: 200 * KB },
  { file: 'timeline-holiday-icon.svg', max: 30 * KB },
  { file: 'timeline-holiday-icon.png', w: 48, h: 48, max: 30 * KB },
  { file: 'timeline-holiday-icon@2x.png', w: 96, h: 96, max: 30 * KB },
  { file: 'timeline-weather-icon.svg', max: 30 * KB },
  { file: 'timeline-weather-icon.png', w: 48, h: 48, max: 30 * KB },
  { file: 'timeline-weather-icon@2x.png', w: 96, h: 96, max: 30 * KB }
];

export function readPngSize(buf) {
  if (buf.length < 24 || buf.readUInt32BE(0) !== PNG_MAGIC) return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

function humanSize(bytes) {
  return (bytes / KB).toFixed(1) + ' KB';
}

async function verify() {
  const issues = [];
  const lines = [];

  for (const item of EXPECTED) {
    const full = resolve(OUT_DIR, item.file);
    let buf;
    let info;
    try {
      info = await stat(full);
      buf = await readFile(full);
    } catch {
      issues.push('缺失文件: ' + item.file);
      continue;
    }

    if (item.file.endsWith('.svg')) {
      const text = buf.toString('utf8');
      if (!text.includes('<svg') || !text.includes('viewBox')) {
        issues.push('SVG 结构异常: ' + item.file);
      }
      if (info.size >= item.max) {
        issues.push('SVG 体积超限: ' + item.file + ' = ' + humanSize(info.size));
      }
      lines.push('ok  ' + item.file + '  (' + humanSize(info.size) + ')');
      continue;
    }

    const size = readPngSize(buf);
    if (!size) {
      issues.push('非合法 PNG: ' + item.file);
      continue;
    }
    if (size.width <= 1 || size.height <= 1) {
      issues.push('仍为占位图: ' + item.file + ' = ' + size.width + 'x' + size.height);
    }
    if (item.w && (size.width !== item.w || size.height !== item.h)) {
      issues.push('尺寸不符: ' + item.file + ' 期望 ' + item.w + 'x' + item.h + ' 实际 ' + size.width + 'x' + size.height);
    }
    if (info.size >= item.max) {
      issues.push('PNG 体积超限: ' + item.file + ' = ' + humanSize(info.size));
    }
    lines.push('ok  ' + item.file + '  ' + size.width + 'x' + size.height + '  ' + humanSize(info.size));
  }

  const ok = issues.length === 0;
  return { ok, issues, lines };
}

const result = await verify();
for (const line of result.lines) process.stdout.write(line + '\n');
if (result.ok) {
  process.stdout.write('校验通过：' + EXPECTED.length + ' 个文件全部合格\n');
} else {
  for (const issue of result.issues) process.stdout.write('✗ ' + issue + '\n');
  process.stdout.write('校验失败：' + result.issues.length + ' 项问题\n');
  process.exit(1);
}