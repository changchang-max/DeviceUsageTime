import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue()
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    host: true,
    open: true,
    proxy: {
      // 所有 REST API(/api/auth、/api/data、/api/user 等)代理到后端 Spring Boot(默认 8080)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket 实时推送代理到后端(协议见 docs/前后端API文档.md 第7章)
      '/ws': {
        target: 'ws://localhost:8080',
        changeOrigin: true,
        ws: true
      }
    }
  }
})
