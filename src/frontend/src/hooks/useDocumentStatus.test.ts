import { renderHook, act } from '@testing-library/react'
import { useDocumentStatus } from './useDocumentStatus'

/**
 * jsdom doesn't implement EventSource, so we provide a mock.
 * Each test gets a fresh mock via beforeEach to avoid leakage.
 */

interface MockEventSource {
  url: string
  withCredentials: boolean
  readyState: number
  onmessage: ((event: MessageEvent) => void) | null
  onerror: (() => void) | null
  close: ReturnType<typeof vi.fn>
}

let mockInstances: MockEventSource[]

class MockEventSourceClass {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2

  url: string
  withCredentials: boolean
  readyState = MockEventSourceClass.OPEN
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: (() => void) | null = null
  close = vi.fn()

  constructor(url: string, options?: { withCredentials?: boolean }) {
    this.url = url
    this.withCredentials = options?.withCredentials ?? false
    mockInstances.push(this)
  }
}

beforeEach(() => {
  mockInstances = []
  vi.stubGlobal('EventSource', MockEventSourceClass)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function simulateMessage(instance: MockEventSource, data: unknown) {
  instance.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }))
}

describe('useDocumentStatus', () => {
  it('returns initial state when documentId is null', () => {
    const { result } = renderHook(() => useDocumentStatus(null))

    expect(result.current.status).toBeNull()
    expect(result.current.isComplete).toBe(false)
    expect(result.current.isFailed).toBe(false)
    expect(mockInstances).toHaveLength(0)
  })

  it('creates EventSource with correct URL and credentials', () => {
    renderHook(() => useDocumentStatus('doc-123'))

    expect(mockInstances).toHaveLength(1)
    expect(mockInstances[0].url).toBe('/api/documents/doc-123/status')
    expect(mockInstances[0].withCredentials).toBe(true)
  })

  it('updates state on PROCESSING message', () => {
    const { result } = renderHook(() => useDocumentStatus('doc-1'))

    act(() => {
      simulateMessage(mockInstances[0], {
        status: 'PROCESSING',
        step: 'Extracting text',
        errorMessage: null,
      })
    })

    expect(result.current.status).toBe('PROCESSING')
    expect(result.current.step).toBe('Extracting text')
    expect(result.current.isComplete).toBe(false)
    expect(result.current.isFailed).toBe(false)
  })

  it('sets isComplete to true on READY', () => {
    const { result } = renderHook(() => useDocumentStatus('doc-1'))

    act(() => {
      simulateMessage(mockInstances[0], {
        status: 'READY',
        step: 'Done',
        errorMessage: null,
      })
    })

    expect(result.current.isComplete).toBe(true)
    expect(result.current.isFailed).toBe(false)
  })

  it('closes EventSource on READY', () => {
    renderHook(() => useDocumentStatus('doc-1'))

    act(() => {
      simulateMessage(mockInstances[0], {
        status: 'READY',
        step: 'Done',
        errorMessage: null,
      })
    })

    expect(mockInstances[0].close).toHaveBeenCalled()
  })

  it('sets isFailed to true on FAILED', () => {
    const { result } = renderHook(() => useDocumentStatus('doc-1'))

    act(() => {
      simulateMessage(mockInstances[0], {
        status: 'FAILED',
        step: 'Error',
        errorMessage: 'Something went wrong',
      })
    })

    expect(result.current.isFailed).toBe(true)
    expect(result.current.errorMessage).toBe('Something went wrong')
  })

  it('ignores malformed JSON messages', () => {
    const { result } = renderHook(() => useDocumentStatus('doc-1'))

    act(() => {
      mockInstances[0].onmessage?.(
        new MessageEvent('message', { data: 'not-json{{{' }),
      )
    })

    // State should remain initial
    expect(result.current.status).toBeNull()
  })

  it('handles onerror with CLOSED readyState', () => {
    const { result } = renderHook(() => useDocumentStatus('doc-1'))

    // First send a PROCESSING message so forDocumentId is set —
    // without this, the memo returns INITIAL_STATE because
    // forDocumentId would still be null after onerror spreads prev state
    act(() => {
      simulateMessage(mockInstances[0], {
        status: 'PROCESSING',
        step: 'Working',
        errorMessage: null,
      })
    })

    act(() => {
      // The hook checks eventSource.readyState === EventSource.CLOSED (2)
      mockInstances[0].readyState = 2
      mockInstances[0].onerror?.()
    })

    expect(result.current.isFailed).toBe(true)
    expect(result.current.errorMessage).toBe('Lost connection to server.')
  })

  it('closes EventSource on unmount', () => {
    const { unmount } = renderHook(() => useDocumentStatus('doc-1'))

    unmount()

    expect(mockInstances[0].close).toHaveBeenCalled()
  })

  it('returns initial state for stale document ID', () => {
    const { result, rerender } = renderHook(
      ({ id }: { id: string | null }) => useDocumentStatus(id),
      { initialProps: { id: 'doc-1' } },
    )

    // Send a message for doc-1
    act(() => {
      simulateMessage(mockInstances[0], {
        status: 'PROCESSING',
        step: 'Working',
        errorMessage: null,
      })
    })

    expect(result.current.status).toBe('PROCESSING')

    // Switch to a different document — old state should not leak
    rerender({ id: 'doc-2' })

    // The hook creates a new EventSource for doc-2, and since no
    // message has arrived yet for doc-2, state should be initial
    expect(result.current.status).toBeNull()
  })
})
