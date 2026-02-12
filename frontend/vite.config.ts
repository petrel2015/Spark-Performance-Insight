import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    define: {
        global: 'window',
    },
    server: {
        port: 3000,
        proxy: {
            '/api': {
                target: 'http://localhost:18081',
                changeOrigin: true
            },
            '/ws-status': {
                target: 'http://localhost:18081',
                ws: true,
                changeOrigin: true
            }
        }
    }
})
