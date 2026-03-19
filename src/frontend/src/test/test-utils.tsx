import { type ReactElement } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom'

interface ProvidersOptions {
  /** Initial route entries for MemoryRouter */
  routerProps?: MemoryRouterProps
}

/**
 * Renders a component wrapped in the providers the app needs:
 * - QueryClientProvider with a fresh QueryClient (retry disabled)
 * - MemoryRouter for react-router-dom Link/useNavigate support
 *
 * A fresh QueryClient per test prevents cache leakage between tests —
 * each test starts with a clean slate, which avoids flaky failures
 * caused by stale data from a previous test run.
 */
export function renderWithProviders(
  ui: ReactElement,
  options?: RenderOptions & ProvidersOptions,
) {
  const { routerProps, ...renderOptions } = options ?? {}

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter {...routerProps}>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }

  return {
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
    queryClient,
  }
}

/**
 * Creates a fresh QueryClient for use with renderHook tests.
 * Same config as renderWithProviders to keep behaviour consistent.
 */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}
