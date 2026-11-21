import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    // 相对路径打包：产物可以挂在任意二级路由下（生产默认 /district360/）
    base: './',
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.VITE_API_PROXY || 'http://localhost:8080', changeOrigin: true },
      },
    },
    build: { chunkSizeWarningLimit: 1500 },
  }
})
