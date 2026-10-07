import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/osm-proxy': {
        target: 'https://overpass-api.de/api/interpreter',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/osm-proxy/, ''),
        secure: false,
      }
    }
  }
})
