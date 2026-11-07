import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // One 670 kB chunk made the first paint wait for the chart library used only by the admin
        // console. Splitting the vendors keeps the entry chunk small and lets the browser cache
        // them independently of the application code.
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }
          if (id.includes('recharts')) {
            return 'charts';
          }
          if (id.includes('victory-vendor') || id.includes('d3-')) {
            return 'd3';
          }
          if (id.includes('lucide-react')) {
            return 'icons';
          }
          return undefined;
        },
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api/blocking': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/blocking/, '/api')
      },
      '/api/reactive': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/reactive/, '/api')
      },
      '/api/relational': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/relational/, '/api')
      },
      '/api/runner': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/runner/, '/api')
      }
    }
  }
});
