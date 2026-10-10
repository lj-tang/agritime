export const PALETTE = {
  paper: '#f4efe3',
  paper2: '#ece5d6',
  ink: '#23301f',
  leaf: '#2f6b40',
  leaf2: '#3f8f56',
  leafSoft: '#e6f0e3',
  sun: '#d99b2b',
  sunBright: '#ffd97a',
  rain: '#3d6f96',
  red: '#c65547',
  line: '#e4ddcb',
  leafLight: '#eaf6e8',
  leafPale: '#c9e6c3'
};

export function buildLogo() {
  return [
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96" fill="none">',
    '  <g transform="scale(4)">',
    '    <circle cx="12" cy="8.4" r="3.6" fill="#ffd97a"/>',
    '    <path d="M12 12.2c0 3.4-1.9 6.2-5.2 8.1.7-2.9 1.9-4.9 3.6-6.1" stroke="#eaf6e8" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>',
    '    <path d="M12 12.2c0 3.4 1.9 6.2 5.2 8.1-.7-2.9-1.9-4.9-3.6-6.1" stroke="#c9e6c3" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>',
    '  </g>',
    '</svg>',
    ''
  ].join('\n');
}

export function buildIndexBg() {
  return [
    '<svg xmlns="http://www.w3.org/2000/svg" width="720" height="1280" viewBox="0 0 720 1280" fill="none">',
    '  <defs>',
    '    <radialGradient id="sunGlow" cx="600" cy="-40" r="560" gradientUnits="userSpaceOnUse">',
    '      <stop offset="0" stop-color="#d99b2b" stop-opacity="0.16"/>',
    '      <stop offset="1" stop-color="#d99b2b" stop-opacity="0"/>',
    '    </radialGradient>',
    '    <radialGradient id="leafGlow" cx="-80" cy="80" r="560" gradientUnits="userSpaceOnUse">',
    '      <stop offset="0" stop-color="#3f8f56" stop-opacity="0.16"/>',
    '      <stop offset="1" stop-color="#3f8f56" stop-opacity="0"/>',
    '    </radialGradient>',
    '  </defs>',
    '  <rect width="720" height="1280" fill="#f4efe3"/>',
    '  <rect width="720" height="1280" fill="url(#sunGlow)"/>',
    '  <rect width="720" height="1280" fill="url(#leafGlow)"/>',
    '  <g stroke="#e4ddcb" stroke-width="3" fill="none" stroke-linecap="round" opacity="0.6">',
    '    <path d="M-20 1010c180-64 360-64 560 0"/>',
    '    <path d="M-20 1090c180-64 360-64 560 0"/>',
    '    <path d="M-20 1170c180-64 360-64 560 0"/>',
    '  </g>',
    '  <path d="M70 1252c30-96 96-128 176-116-40 84-104 130-176 116z" fill="#e6f0e3" opacity="0.7"/>',
    '  <path d="M544 1272c20-70 66-94 120-86-28 60-72 94-120 86z" fill="#e6f0e3" opacity="0.55"/>',
    '</svg>',
    ''
  ].join('\n');
}

export function buildHolidayIcon() {
  return [
    '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48" fill="none">',
    '  <path d="M8 12h32v7H8z" stroke="#c65547" stroke-width="3.4" stroke-linejoin="round"/>',
    '  <path d="M10 19h28v21H10z" stroke="#c65547" stroke-width="3.4" stroke-linejoin="round"/>',
    '  <path d="M24 12v28" stroke="#d99b2b" stroke-width="3.4" stroke-linecap="round"/>',
    '  <path d="M24 11 17.5 4.5M24 11 30.5 4.5" stroke="#c65547" stroke-width="3.4" stroke-linecap="round"/>',
    '</svg>',
    ''
  ].join('\n');
}

export function buildWeatherIcon() {
  return [
    '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48" fill="none">',
    '  <circle cx="18" cy="17" r="6.4" stroke="#d99b2b" stroke-width="3.4"/>',
    '  <path d="M13 34a5.2 5.2 0 0 1 .6-10.3 7.4 7.4 0 0 1 14 1.8A4.8 4.8 0 0 1 27 34z" fill="#3d6f96"/>',
    '  <path d="M19 38l-1.4 4M27 38l-1.4 4" stroke="#3d6f96" stroke-width="3.4" stroke-linecap="round"/>',
    '</svg>',
    ''
  ].join('\n');
}