import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：
    // - AutoImport 处理 ElMessage / ElMessageBox 等函数式 API 的样式
    // - Components 处理模板里用到的 el-xxx 组件与其样式
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      // 只自动注册 el-* 前缀组件，业务组件仍显式 import，避免隐式依赖
      dirs: [],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    // 主包体积告警阈值
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        // 只拆体积大且稳定的 Vue 运行时；
        // Element Plus 交给 unplugin 按需引入与 tree-shaking，不要手动整包塞进 chunk
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia']
        }
      }
    }
  },
  server: {
    port: 3000,
    host: true,
    open: true,
    proxy: {
      // REST API(/api/auth、/api/data、/api/user 等)代理到后端 Spring Boot(默认 8080)
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