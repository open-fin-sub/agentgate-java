import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 配置:前端代理后端 API 到 Spring Boot
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5274,
    proxy: {
      '/agentgate': {
        target: 'http://127.0.0.1:8080/race-api',
        changeOrigin: true
      },
      '/user': {
        target: 'http://127.0.0.1:8080/race-api',
        changeOrigin: true,
        bypass(req) {
          if (req.headers.accept?.includes('text/html')) {
            return '/index.html'
          }
        }
      }
    }
  }
})
