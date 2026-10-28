import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Build: a kész fájlok a Spring Boot "static" mappájába kerülnek (target/classes/static), így a backend
// szolgálja ki őket a / címen – egy szerver, egy port, nincs CORS.
// Fejlesztés: "npm run dev" -> http://localhost:5173, az /api és /actuator kéréseket a Vite
// továbbítja a 8080-on futó backendnek (szintén nincs CORS).
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
});
