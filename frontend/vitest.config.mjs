import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    include: ['src/**/*.spec.js'],
    environment: 'jsdom',
    clearMocks: true,
    setupFiles: ['./src/test/setup.js']
  }
})
