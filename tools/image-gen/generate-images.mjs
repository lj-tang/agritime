import { writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { Resvg } from '@resvg/resvg-js';
import {
  buildLogo,
  buildIndexBg,
  buildHolidayIcon,
  buildWeatherIcon
} from './svg-builders.js';

const DEFAULT_OUT = resolve(import.meta.dirname, '..', '..', 'frontend', 'assets', 'img');

const ASSETS = [
  { name: 'common-logo', build: buildLogo, pngs: [{ w: 96, suffix: '' }, { w: 192, suffix: '@2x' }] },
  { name: 'index-bg', build: buildIndexBg, pngs: [{ w: 720, suffix: '' }] },
  { name: 'timeline-holiday-icon', build: buildHolidayIcon, pngs: [{ w: 48, suffix: '' }, { w: 96, suffix: '@2x' }] },
  { name: 'timeline-weather-icon', build: buildWeatherIcon, pngs: [{ w: 48, suffix: '' }, { w: 96, suffix: '@2x' }] }
];

export function exportPng(svg, width) {
  const resvg = new Resvg(svg, {
    fitTo: { mode: 'width', value: width },
    background: 'rgba(0,0,0,0)'
  });
  return resvg.render().asPng();
}

async function main(argv) {
  const onlyIdx = argv.indexOf('--only');
  const only = onlyIdx !== -1 ? argv[onlyIdx + 1] : null;
  const outIdx = argv.indexOf('--out');
  const outDir = outIdx !== -1 ? resolve(argv[outIdx + 1]) : DEFAULT_OUT;

  await mkdir(outDir, { recursive: true });

  let count = 0;
  for (const asset of ASSETS) {
    if (only && asset.name !== only) continue;
    const svg = asset.build();
    await writeFile(resolve(outDir, asset.name + '.svg'), svg, 'utf8');
    count += 1;
    for (const p of asset.pngs) {
      await writeFile(resolve(outDir, asset.name + p.suffix + '.png'), exportPng(svg, p.w));
      count += 1;
    }
  }
  process.stdout.write('生成完成：' + count + ' 个文件 -> ' + outDir + '\n');
}

main(process.argv.slice(2)).catch((err) => {
  process.stderr.write('生成失败：' + (err && err.message ? err.message : String(err)) + '\n');
  process.exit(1);
});