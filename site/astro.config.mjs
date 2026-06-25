import { defineConfig } from 'astro/config';

import sitemap from '@astrojs/sitemap';

// The site is served from the apex custom domain (GitHub Pages). `site` drives
// canonical URLs + sitemap; there's no base path since it lives at the root.
export default defineConfig({
  site: 'https://mc3dprint.dev',
  integrations: [sitemap()],
});