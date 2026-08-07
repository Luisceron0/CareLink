import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El proxy de /api evita CORS en desarrollo y, más importante, hace que el frontend
// hable siempre con rutas relativas: no hay una URL de backend embebida en el bundle
// que haya que cambiar por entorno (ni que pueda quedar apuntando a otro lado).
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://backend:8080',
        changeOrigin: true,
      },
    },
  },
})
