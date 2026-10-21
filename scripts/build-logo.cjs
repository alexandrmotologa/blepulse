// BLEPulse Developer Branding Mascot Logo Generator
// Mascot: The Echolocation Sentinel Bat (Radar Microbat)
// Metaphor: Nature's high-frequency pulse scanner and proximity echolocation sensor
const fs = require('fs');
const path = require('path');
const puppeteer = require('B:/workgit/omnivault/node_modules/puppeteer-core');

const ROOT_DIR = 'B:/workgit/blepulse';
const OUTPUT_DIR = path.join(ROOT_DIR, 'docs', 'images');

function buildLogoSvg() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <defs>
    <clipPath id="squircle-clip">
      <rect x="24" y="24" width="976" height="976" rx="220" />
    </clipPath>

    <!-- Gradients -->
    <linearGradient id="cyan-glow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#00f5ff"/>
      <stop offset="100%" stop-color="#0284c7"/>
    </linearGradient>

    <linearGradient id="cyan-bright" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#38bdf8"/>
      <stop offset="100%" stop-color="#00f5ff"/>
    </linearGradient>

    <linearGradient id="amber-accent" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#fbbf24"/>
      <stop offset="100%" stop-color="#f59e0b"/>
    </linearGradient>

    <linearGradient id="facet-lit" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#64748b"/>
      <stop offset="100%" stop-color="#475569"/>
    </linearGradient>

    <linearGradient id="facet-mid" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#334155"/>
      <stop offset="100%" stop-color="#1e293b"/>
    </linearGradient>

    <linearGradient id="facet-dark" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#1e293b"/>
      <stop offset="100%" stop-color="#0f172a"/>
    </linearGradient>

    <linearGradient id="facet-deep" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#0f172a"/>
      <stop offset="100%" stop-color="#0b0f19"/>
    </linearGradient>

    <filter id="subtle-shadow" x="-15%" y="-15%" width="130%" height="130%">
      <feDropShadow dx="0" dy="16" stdDeviation="20" flood-color="#000000" flood-opacity="0.15" />
    </filter>
  </defs>

  <!-- Luxury White Squircle Container -->
  <rect x="24" y="24" width="976" height="976" rx="220" fill="#ffffff" stroke="#e2e8f0" stroke-width="6" />

  <g clip-path="url(#squircle-clip)">
    <g transform="translate(512, 512)" filter="url(#subtle-shadow)">

      <!-- Hexagonal Architectural Gateway Frame -->
      <polygon points="
        0,-395
        342,-198
        342,198
        0,395
        -342,198
        -342,-198
      " fill="none" stroke="#0f172a" stroke-width="40" stroke-linejoin="round" />

      <!-- Inner Dashed Telemetry Hexagon -->
      <polygon points="
        0,-360
        312,-180
        312,180
        0,360
        -312,180
        -312,-180
      " fill="none" stroke="#00f5ff" stroke-width="4" opacity="0.45" stroke-dasharray="16, 12" />

      <!-- Echolocation Sonar Acoustic Arcs (Emitters at top) -->
      <path d="M -160,-280 A 180,180 0 0,1 160,-280" fill="none" stroke="#00f5ff" stroke-width="3.5" opacity="0.3" stroke-linecap="round" />
      <path d="M -210,-310 A 240,240 0 0,1 210,-310" fill="none" stroke="#00f5ff" stroke-width="3" opacity="0.2" stroke-linecap="round" />

      <!-- 100% Watertight Solid Base Silhouette (Zero-Leak Path) -->
      <path d="
        M 0,-140
        L 45,-210 L 95,-325 L 145,-335 L 175,-250 L 160,-175 L 210,-190 L 295,-130 L 325,-25 L 305,95 L 265,190 L 195,280 L 110,320 L 0,335
        L -110,320 L -195,280 L -265,190 L -305,95 L -325,-25 L -295,-130 L -210,-190 L -160,-175 L -175,-250 L -145,-335 L -95,-325 L -45,-210
        Z
      " fill="#0b0f19" />

      <!-- Wing Facets: Outer Left Wing -->
      <polygon points="-160,-175 -295,-130 -325,-25 -215,-70" fill="url(#facet-dark)" />
      <polygon points="-295,-130 -325,-25 -305,95 -235,30" fill="url(#facet-mid)" />
      <polygon points="-305,95 -265,190 -195,140 -235,30" fill="url(#facet-lit)" />
      <polygon points="-265,190 -195,280 -140,205 -195,140" fill="url(#facet-mid)" />
      <polygon points="-195,280 -110,320 -85,225 -140,205" fill="url(#facet-dark)" />

      <!-- Wing Facets: Outer Right Wing (Symmetrical) -->
      <polygon points="160,-175 295,-130 325,-25 215,-70" fill="url(#facet-lit)" />
      <polygon points="295,-130 325,-25 305,95 235,30" fill="url(#facet-mid)" />
      <polygon points="305,95 265,190 195,140 235,30" fill="url(#facet-dark)" />
      <polygon points="265,190 195,280 140,205 195,140" fill="url(#facet-mid)" />
      <polygon points="195,280 110,320 85,225 140,205" fill="url(#facet-lit)" />

      <!-- Inner Wing Ribs & Fold Geometry -->
      <polygon points="-160,-175 -215,-70 -125,-40 -90,-120" fill="url(#facet-lit)" />
      <polygon points="160,-175 215,-70 125,-40 90,-120" fill="url(#facet-mid)" />

      <polygon points="-215,-70 -235,30 -140,20 -125,-40" fill="url(#facet-dark)" />
      <polygon points="215,-70 235,30 140,20 125,-40" fill="url(#facet-lit)" />

      <polygon points="-235,30 -195,140 -105,100 -140,20" fill="url(#facet-mid)" />
      <polygon points="235,30 195,140 105,100 140,20" fill="url(#facet-dark)" />

      <polygon points="-195,140 -140,205 -70,165 -105,100" fill="url(#facet-lit)" />
      <polygon points="195,140 140,205 70,165 105,100" fill="url(#facet-mid)" />

      <!-- Large Radar Dish Ears: Left Ear -->
      <polygon points="-45,-210 -95,-325 -145,-335 -105,-240" fill="url(#facet-lit)" />
      <polygon points="-95,-325 -145,-335 -175,-250 -130,-225" fill="url(#facet-mid)" />
      <polygon points="-145,-335 -175,-250 -160,-175 -125,-185" fill="url(#facet-dark)" />
      <!-- Left Ear Inner Acoustic Concha (Electric Cyan) -->
      <polygon points="-95,-300 -138,-315 -148,-240 -105,-220" fill="url(#cyan-glow)" opacity="0.9" />
      <polygon points="-105,-220 -148,-240 -138,-195 -95,-180" fill="url(#facet-dark)" />

      <!-- Large Radar Dish Ears: Right Ear (Symmetrical) -->
      <polygon points="45,-210 95,-325 145,-335 105,-240" fill="url(#facet-mid)" />
      <polygon points="95,-325 145,-335 175,-250 130,-225" fill="url(#facet-lit)" />
      <polygon points="145,-335 175,-250 160,-175 125,-185" fill="url(#facet-dark)" />
      <!-- Right Ear Inner Acoustic Concha (Electric Cyan) -->
      <polygon points="95,-300 138,-315 148,-240 105,-220" fill="url(#cyan-bright)" opacity="0.9" />
      <polygon points="105,-220 148,-240 138,-195 95,-180" fill="url(#facet-mid)" />

      <!-- Head & Cranial Crest -->
      <polygon points="0,-235 -45,-210 -65,-155 0,-175" fill="url(#facet-lit)" />
      <polygon points="0,-235 45,-210 65,-155 0,-175" fill="url(#facet-mid)" />

      <polygon points="0,-175 -65,-155 -55,-105 0,-115" fill="url(#facet-mid)" />
      <polygon points="0,-175 65,-155 55,-105 0,-115" fill="url(#facet-dark)" />

      <!-- Aerodynamic Zygomatic Cheeks & Muzzle -->
      <polygon points="0,-115 -55,-105 -45,-60 0,-68" fill="url(#facet-dark)" />
      <polygon points="0,-115 55,-105 45,-60 0,-68" fill="url(#facet-lit)" />

      <polygon points="0,-68 -45,-60 -25,-25 0,-30" fill="url(#facet-mid)" />
      <polygon points="0,-68 45,-60 25,-25 0,-30" fill="url(#facet-lit)" />

      <!-- Nose Leaf Radar Tragus -->
      <polygon points="0,-52 -16,-38 0,-32" fill="url(#cyan-glow)" />
      <polygon points="0,-52 16,-38 0,-32" fill="url(#cyan-bright)" />
      <polygon points="0,-32 -14,-22 0,-18" fill="#0f172a" />
      <polygon points="0,-32 14,-22 0,-18" fill="#1e293b" />

      <!-- Almond Predator Eyes with Slit Pupils (Electric Cyan) -->
      <!-- Left Eye -->
      <polygon points="-28,-90 -48,-82 -32,-74 -18,-80" fill="#0b0f19" />
      <polygon points="-26,-88 -45,-82 -31,-76 -20,-81" fill="url(#cyan-glow)" />
      <polygon points="-33,-88 -37,-82 -32,-76 -30,-82" fill="#ffffff" />

      <!-- Right Eye -->
      <polygon points="28,-90 48,-82 32,-74 18,-80" fill="#0b0f19" />
      <polygon points="26,-88 45,-82 31,-76 20,-81" fill="url(#cyan-bright)" />
      <polygon points="33,-88 37,-82 32,-76 30,-82" fill="#ffffff" />

      <!-- Muscular Obsidian Armor Torso -->
      <polygon points="0,-18 -42,15 -35,90 0,75" fill="url(#facet-lit)" />
      <polygon points="0,-18 42,15 35,90 0,75" fill="url(#facet-mid)" />

      <polygon points="-42,15 -90,-10 -75,60 -35,90" fill="url(#facet-mid)" />
      <polygon points="42,15 90,-10 75,60 35,90" fill="url(#facet-dark)" />

      <!-- Central BLE Pulse Telemetry Core (Sine/Rune Facet) -->
      <polygon points="0,75 -35,90 -28,170 0,185" fill="url(#facet-mid)" />
      <polygon points="0,75 35,90 28,170 0,185" fill="url(#facet-lit)" />

      <!-- Stylized Pulse Rune / Telemetry Wave in Chest -->
      <path d="
        M -24,130
        L -12,130
        L -6,112
        L 0,152
        L 6,118
        L 12,130
        L 24,130
      " fill="none" stroke="#00f5ff" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round" />

      <!-- Abdominal Armor & Tail Tip -->
      <polygon points="0,185 -28,170 -20,250 0,265" fill="url(#facet-dark)" />
      <polygon points="0,185 28,170 20,250 0,265" fill="url(#facet-mid)" />

      <polygon points="0,265 -20,250 0,315" fill="url(#facet-lit)" />
      <polygon points="0,265 20,250 0,315" fill="url(#facet-mid)" />

      <polygon points="-20,250 -85,225 -50,290 0,315" fill="url(#facet-dark)" />
      <polygon points="20,250 85,225 50,290 0,315" fill="url(#facet-lit)" />

      <!-- Lower Tail Spine & Claws -->
      <polygon points="0,315 -35,328 0,335" fill="url(#facet-mid)" />
      <polygon points="0,315 35,328 0,335" fill="url(#facet-dark)" />

      <!-- Cyan Telemetry Accent Dots on Gateway Node Intersections -->
      <circle cx="0" cy="-395" r="7" fill="#00f5ff" />
      <circle cx="342" cy="-198" r="7" fill="#00f5ff" />
      <circle cx="342" cy="198" r="7" fill="#00f5ff" />
      <circle cx="0" cy="395" r="7" fill="#00f5ff" />
      <circle cx="-342" cy="198" r="7" fill="#00f5ff" />
      <circle cx="-342" cy="-198" r="7" fill="#00f5ff" />

    </g>
  </g>
</svg>`;
}

async function renderLogo() {
  const svg = buildLogoSvg();
  const svgPath = path.join(OUTPUT_DIR, 'logo.svg');
  const pngPath = path.join(OUTPUT_DIR, 'logo.png');
  const altPngPath = path.join(OUTPUT_DIR, 'blepulse_logo.png');

  fs.writeFileSync(svgPath, svg);
  console.log('✓ Wrote docs/images/logo.svg');

  // Launch Chrome headless to render 1024x1024 PNG
  const browser = await puppeteer.launch({
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--force-device-scale-factor=1'],
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1024, height: 1024, deviceScaleFactor: 1 });

  const html = `<!DOCTYPE html>
<html>
<head>
  <style>
    * { margin: 0; padding: 0; }
    body { background: transparent; overflow: hidden; }
    svg { display: block; width: 1024px; height: 1024px; }
  </style>
</head>
<body>${svg}</body>
</html>`;

  await page.setContent(html, { waitUntil: 'networkidle0' });
  await page.screenshot({ path: pngPath, omitBackground: true });
  fs.copyFileSync(pngPath, altPngPath);
  await browser.close();

  console.log('✓ Rendered docs/images/logo.png and blepulse_logo.png at 1024x1024');
}

renderLogo().catch((err) => {
  console.error('Failed to generate logo:', err);
  process.exit(1);
});
