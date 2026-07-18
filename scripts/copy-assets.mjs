// Copia as fontes IBM Plex (subset latino, cobre acentos pt-BR) de node_modules
// para src/main/resources/static/fonts. Roda no `npm run build` (frontend-maven-plugin).
import {copyFileSync, mkdirSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'src/main/resources/static/fonts');
mkdirSync(out, {recursive: true});

const files = [
    ['@fontsource/ibm-plex-sans/files', 'ibm-plex-sans-latin-400-normal.woff2'],
    ['@fontsource/ibm-plex-sans/files', 'ibm-plex-sans-latin-400-italic.woff2'],
    ['@fontsource/ibm-plex-sans/files', 'ibm-plex-sans-latin-500-normal.woff2'],
    ['@fontsource/ibm-plex-sans/files', 'ibm-plex-sans-latin-600-normal.woff2'],
    ['@fontsource/ibm-plex-sans/files', 'ibm-plex-sans-latin-700-normal.woff2'],
    ['@fontsource/ibm-plex-mono/files', 'ibm-plex-mono-latin-400-normal.woff2'],
    ['@fontsource/ibm-plex-mono/files', 'ibm-plex-mono-latin-500-normal.woff2'],
];

for (const [pkgDir, file] of files) {
    copyFileSync(join(root, 'node_modules', pkgDir, file), join(out, file));
}
console.log(`copy-assets: ${files.length} fontes copiadas para static/fonts`);
