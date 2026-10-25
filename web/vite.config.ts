import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // 정적 호스팅(Vercel, Netlify, 하위 경로)에서도 동작하도록 상대 경로로 빌드
  base: './',
  server: { port: 5173 },
})
