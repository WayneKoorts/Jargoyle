import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useMessages } from './useMessages'
import type { ReactNode } from 'react'

function createWrapper() {
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }

  return { Wrapper, queryClient }
}

describe('useMessages', () => {
  it('returns empty array while loading', () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useMessages('conv-1'), { wrapper: Wrapper })

    expect(result.current.messages).toEqual([])
    expect(result.current.isLoading).toBe(true)
  })

  it('returns messages in chronological order from a single page', async () => {
    // The default MSW handler returns messages newest-first:
    // [msg-2 (ASSISTANT), msg-1 (USER)]
    // After reversal, we expect: [msg-1 (USER), msg-2 (ASSISTANT)]
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useMessages('conv-1'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.messages).toHaveLength(2)
    expect(result.current.messages[0].id).toBe('msg-1')
    expect(result.current.messages[0].role).toBe('USER')
    expect(result.current.messages[1].id).toBe('msg-2')
    expect(result.current.messages[1].role).toBe('ASSISTANT')
  })

  it('reports hasMore as false when on the last page', async () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useMessages('conv-1'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    // Default handler returns last: true
    expect(result.current.hasMore).toBe(false)
  })

  it('reports hasMore as true when more pages exist', async () => {
    server.use(
      http.get('/api/conversations/:conversationId/messages', () => {
        return HttpResponse.json({
          content: [
            { id: 'msg-4', role: 'ASSISTANT', content: 'Response', sourceChunks: null, createdAt: '2026-03-20T10:03:00Z' },
            { id: 'msg-3', role: 'USER', content: 'Question', sourceChunks: null, createdAt: '2026-03-20T10:02:00Z' },
          ],
          number: 0,
          size: 2,
          totalElements: 4,
          totalPages: 2,
          numberOfElements: 2,
          first: true,
          last: false,
          empty: false,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useMessages('conv-1'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.hasMore).toBe(true)
  })

  it('handles empty conversation', async () => {
    server.use(
      http.get('/api/conversations/:conversationId/messages', () => {
        return HttpResponse.json({
          content: [],
          number: 0,
          size: 50,
          totalElements: 0,
          totalPages: 0,
          numberOfElements: 0,
          first: true,
          last: true,
          empty: true,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useMessages('conv-1'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.messages).toEqual([])
    expect(result.current.hasMore).toBe(false)
  })

  it('reverses multi-page data into chronological order', async () => {
    // Simulate a scenario where two pages have already been fetched.
    // Page 0 (newest): [msg-4, msg-3]
    // Page 1 (older):  [msg-2, msg-1]
    // Expected chronological output: [msg-1, msg-2, msg-3, msg-4]

    const { Wrapper, queryClient } = createWrapper()

    // Pre-seed the cache with two pages to test the reversal logic
    // without needing to trigger fetchNextPage
    queryClient.setQueryData(['messages', 'conv-multi'], {
      pages: [
        {
          content: [
            { id: 'msg-4', role: 'ASSISTANT', content: 'Fourth', sourceChunks: null, createdAt: '2026-03-20T10:04:00Z' },
            { id: 'msg-3', role: 'USER', content: 'Third', sourceChunks: null, createdAt: '2026-03-20T10:03:00Z' },
          ],
          number: 0,
          size: 2,
          totalElements: 4,
          totalPages: 2,
          numberOfElements: 2,
          first: true,
          last: false,
          empty: false,
        },
        {
          content: [
            { id: 'msg-2', role: 'ASSISTANT', content: 'Second', sourceChunks: null, createdAt: '2026-03-20T10:02:00Z' },
            { id: 'msg-1', role: 'USER', content: 'First', sourceChunks: null, createdAt: '2026-03-20T10:01:00Z' },
          ],
          number: 1,
          size: 2,
          totalElements: 4,
          totalPages: 2,
          numberOfElements: 2,
          first: false,
          last: true,
          empty: false,
        },
      ],
      pageParams: [0, 1],
    })

    const { result } = renderHook(() => useMessages('conv-multi'), { wrapper: Wrapper })

    // Data is pre-seeded so no loading state
    expect(result.current.messages).toHaveLength(4)
    expect(result.current.messages.map(m => m.id)).toEqual([
      'msg-1', 'msg-2', 'msg-3', 'msg-4',
    ])
  })
})
