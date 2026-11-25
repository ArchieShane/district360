import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    // 相对路径打包：产物可以挂在任意二级路由下
    base: './',
    plugins: [vue()],
    server: {
      // 监听所有网卡：同网段可以用本机 IP 访问开发服务器（如 http://10.255.254.151:5174）
      host: true,
      port: 5174,
      proxy: {
        '/api': { target: env.VITE_API_PROXY || 'http://localhost:8080', changeOrigin: true },
      },
    },
    build: { chunkSizeWarningLimit: 1500 },
  }
})
