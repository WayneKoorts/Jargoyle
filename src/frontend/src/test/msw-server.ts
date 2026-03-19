import { setupServer } from 'msw/node'
import { handlers } from './msw-handlers'

/**
 * MSW server instance for test use. Started in setup.ts via
 * beforeAll/afterEach/afterAll lifecycle hooks.
 */
export const server = setupServer(...handlers)
