import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

/**
 * 前端构建配置只保留当前项目真正用到的能力：
 * Vue、Tailwind、路径别名和本地联调代理。
 */
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      // 统一用 @ 指向 src，减少跨层级相对路径带来的阅读噪音。
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        // 本地开发把 /api 转发到 Spring Boot，页面代码无需关心真实域名。
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 后端真实接口不带 /api 前缀，所以在代理层统一裁掉。
        rewrite: (url) => url.replace(/^\/api/, ''),
      },
    },
  },
})
