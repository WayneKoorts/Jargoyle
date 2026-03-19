import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      include: ['src/**/*.test.{ts,tsx}'],
      reporters: process.env.CI
        ? ['default', 'junit']
        : ['default'],
      outputFile: {
        junit: 'test-results/junit.xml',
      },
    },
  }),
)
