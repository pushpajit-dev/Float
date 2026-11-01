const fs = require('fs');
const path = require('path');

const iconPath = 'C:/Users/pushp/.gemini/antigravity/scratch/Float/public/icon-512.png';
const b64 = fs.readFileSync(iconPath).toString('base64');
const svg = `<svg width="512" height="512" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Float">
  <image href="data:image/png;base64,${b64}" width="512" height="512" />
</svg>
`;

fs.writeFileSync('C:/Users/pushp/.gemini/antigravity/scratch/Float/public/favicon.svg', svg);
fs.writeFileSync('C:/Users/pushp/.gemini/antigravity/scratch/Float/public/icon.svg', svg);
fs.writeFileSync('C:/Users/pushp/.gemini/antigravity/scratch/Float/public/icon-maskable.svg', svg);

console.log('SVG icons generated successfully.');
