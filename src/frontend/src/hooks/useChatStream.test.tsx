import { renderHook, act } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '../test/test-utils'
import { useChatStream } from './useChatStream'
import type { ChatStreamEvent } from '../api/conversations'
import type { ReactNode } from 'react'

// Mock the streamChat function — the rest of the module stays real
vi.mock('../api/conversations', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/conversations')>()
  return { ...actual, streamChat: vi.fn() }
})

// Import the mocked version so we can configure it per test
import { streamChat } from '../api/conversations'
const mockStreamChat = vi.mocked(streamChat)

/** Creates an async generator that yields the given events in sequence. */
async function* mockStream(...events: ChatStreamEvent[]): AsyncGenerator<ChatStreamEvent> {
  for (const event of events) {
    yield event
  }
}

/** Creates an async generator that throws an error. */
async function* errorStream(): AsyncGenerator<ChatStreamEvent> {
  yield* [] // satisfy require-yield — the throw below fires before any yield
  throw new Error('Network failure')
}

function createWrapper() {
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }

  return { Wrapper, queryClient }
}

describe('useChatStream', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('accumulates TOKEN events into streamingContent', async () => {
    mockStreamChat.mockReturnValue(mockStream(
      { type: 'TOKEN', content: 'Hello ', messageId: null, sourceChunks: null },
      { type: 'TOKEN', content: 'world', messageId: null, sourceChunks: null },
      { type: 'COMPLETE', content: null, messageId: 'msg-1', sourceChunks: [] },
    ))

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('Hi')
    })

    // After COMPLETE, streamingContent is cleared
    expect(result.current.streamingContent).toBe('')
    expect(result.current.isStreaming).toBe(false)
  })

  it('creates an optimistic user message immediately', async () => {
    // Use a stream that yields events we can inspect before it completes
    mockStreamChat.mockReturnValue(mockStream(
      { type: 'COMPLETE', content: null, messageId: 'msg-1', sourceChunks: [] },
    ))

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('What is this?')
    })

    // After COMPLETE, the optimistic message is cleared (replaced by server data)
    expect(result.current.optimisticMessage).toBeNull()
  })

  it('sets isStreaming to true during streaming and false after', async () => {
    mockStreamChat.mockReturnValue(mockStream(
      { type: 'TOKEN', content: 'Hi', messageId: null, sourceChunks: null },
      { type: 'COMPLETE', content: null, messageId: 'msg-1', sourceChunks: [] },
    ))

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    expect(result.current.isStreaming).toBe(false)

    await act(async () => {
      await result.current.sendMessage('test')
    })

    expect(result.current.isStreaming).toBe(false)
  })

  it('sets error on ERROR event', async () => {
    mockStreamChat.mockReturnValue(mockStream(
      { type: 'ERROR', content: 'Rate limit exceeded.', messageId: null, sourceChunks: null },
    ))

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('test')
    })

    expect(result.current.error).toBe('Rate limit exceeded.')
  })

  it('sets fallback error when streamChat throws', async () => {
    mockStreamChat.mockReturnValue(errorStream())

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('test')
    })

    expect(result.current.error).toBe('Failed to send message. Please try again.')
    expect(result.current.isStreaming).toBe(false)
  })

  it('invalidates messages and conversations queries on COMPLETE', async () => {
    mockStreamChat.mockReturnValue(mockStream(
      { type: 'COMPLETE', content: null, messageId: 'msg-1', sourceChunks: [] },
    ))

    const { Wrapper, queryClient } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('test')
    })

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['messages', 'conv-1'] }),
    )
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['conversations'] }),
    )
  })

  it('clears previous error when sending a new message', async () => {
    // First call: error
    mockStreamChat.mockReturnValueOnce(mockStream(
      { type: 'ERROR', content: 'Something broke.', messageId: null, sourceChunks: null },
    ))

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('first')
    })

    expect(result.current.error).toBe('Something broke.')

    // Second call: success — error should be cleared
    mockStreamChat.mockReturnValueOnce(mockStream(
      { type: 'COMPLETE', content: null, messageId: 'msg-2', sourceChunks: [] },
    ))

    await act(async () => {
      await result.current.sendMessage('retry')
    })

    expect(result.current.error).toBeNull()
  })

  it('handles ERROR event with null content gracefully', async () => {
    mockStreamChat.mockReturnValue(mockStream(
      { type: 'ERROR', content: null, messageId: null, sourceChunks: null },
    ))

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useChatStream('conv-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.sendMessage('test')
    })

    expect(result.current.error).toBe('Something went wrong.')
  })
})
