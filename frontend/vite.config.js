import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// strictPort: fail instead of silently moving to another port, because the backend's CORS
// config only allows http://localhost:5173
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
})
