import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:9900',
      '/milvus': 'http://localhost:9900',
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
