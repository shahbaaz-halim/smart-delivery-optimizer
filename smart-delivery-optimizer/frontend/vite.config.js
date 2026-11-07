import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin API calls in development; mirrors the nginx proxy used in containers.
    proxy: {
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
    },
  },
})
