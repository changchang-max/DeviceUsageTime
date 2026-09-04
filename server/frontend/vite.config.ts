import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteMockServe } from 'vite-plugin-mock'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    viteMockServe({
      mockPath: 'mock',
      enable: true,
    })
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
      // 认证相关接口代理到后端 Spring Boot(默认 8080)。
      // /api/data、/api/user 暂仍由 vite-plugin-mock 提供,故代理范围仅限 /api/auth
      '/api/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
