import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8091',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 构建后的 JS/CSS 通过 CDN 加载
    rollupOptions: {
      output: {
        // 内联小资源
        inlineDynamicImports: true,
      },
    },
  },
})
