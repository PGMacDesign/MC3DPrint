import { defineConfig } from 'astro/config';

// The site is served from the apex custom domain (GitHub Pages). `site` drives
// canonical URLs + sitemap; there's no base path since it lives at the root.
export default defineConfig({
  site: 'https://mc3dprint.dev',
});
