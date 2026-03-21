import {
  createConversation,
  fetchConversations,
  fetchMessages,
  streamChat,
} from './conversations'

/**
 * Builds a Response whose body is a ReadableStream of SSE-formatted events.
 * Each string in `events` becomes a `data: {string}\n\n` frame.
 */
function createSSEResponse(...events: string[]): Response {
  const sseText = events.map(e => `data: ${e}\n\n`).join('')
  const encoded = new TextEncoder().encode(sseText)
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(encoded)
      controller.close()
    },
  })
  return new Response(stream, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

describe('createConversation', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('POSTs to the correct URL and returns the result', async () => {
    const body = {
      id: 'conv-1',
      documentId: 'doc-1',
      suggestedQuestions: [{ text: 'What is this?', category: 'General' }],
    }
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 201 }),
    )

    const result = await createConversation('doc-1')

    expect(result).toEqual(body)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/documents/doc-1/conversations',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    )
  })
})

describe('fetchConversations', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('GETs conversations for a document', async () => {
    const body = [
      {
        id: 'conv-1',
        documentId: 'doc-1',
        title: 'Chat',
        messageCount: 3,
        createdAt: '2026-03-20T10:00:00Z',
        lastMessageAt: '2026-03-20T10:05:00Z',
      },
    ]
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )

    const result = await fetchConversations('doc-1')

    expect(result).toEqual(body)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/documents/doc-1/conversations',
      expect.objectContaining({ credentials: 'include' }),
    )
  })
})

describe('fetchMessages', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches page 0 by default with size 50', async () => {
    const body = {
      content: [],
      number: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      numberOfElements: 0,
      first: true,
      last: true,
      empty: true,
    }
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )

    const result = await fetchMessages('conv-1')

    expect(result).toEqual(body)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/conversations/conv-1/messages?page=0&size=50',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('passes explicit page number', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ content: [] }), { status: 200 }),
    )

    await fetchMessages('conv-1', 3)

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/conversations/conv-1/messages?page=3&size=50',
      expect.anything(),
    )
  })
})

describe('streamChat', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('yields TOKEN and COMPLETE events from an SSE stream', async () => {
    const tokenEvent = JSON.stringify({ type: 'TOKEN', content: 'Hello', messageId: null, sourceChunks: null })
    const completeEvent = JSON.stringify({
      type: 'COMPLETE',
      content: null,
      messageId: 'msg-1',
      sourceChunks: [{ chunkId: 'c-1', chunkIndex: 0, preview: 'Some text' }],
    })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(createSSEResponse(tokenEvent, completeEvent))

    const events = []
    for await (const event of streamChat('conv-1', 'What is this?')) {
      events.push(event)
    }

    expect(events).toHaveLength(2)
    expect(events[0]).toEqual({ type: 'TOKEN', content: 'Hello', messageId: null, sourceChunks: null })
    expect(events[1].type).toBe('COMPLETE')
    expect(events[1].messageId).toBe('msg-1')
    expect(events[1].sourceChunks).toHaveLength(1)
  })

  it('sends POST with correct URL, credentials, and body', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      createSSEResponse(JSON.stringify({ type: 'COMPLETE', content: null, messageId: 'msg-1', sourceChunks: [] })),
    )

    // Consume the generator to trigger the fetch
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    for await (const _event of streamChat('conv-42', 'Tell me more')) {
      // drain
    }

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/conversations/conv-42/messages',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: 'Tell me more' }),
      }),
    )
  })

  it('throws on non-OK response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 403 }),
    )

    const generator = streamChat('conv-1', 'test')
    await expect(generator.next()).rejects.toThrow('Chat error: 403')
  })

  it('handles SSE data split across multiple chunks', async () => {
    // Simulate the server sending one SSE event split across two read() calls
    const fullSSE = 'data: {"type":"TOKEN","content":"Hi","messageId":null,"sourceChunks":null}\n\n'
    const encoder = new TextEncoder()
    const splitPoint = 20 // Split in the middle of the JSON

    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(fullSSE.slice(0, splitPoint)))
        controller.enqueue(encoder.encode(fullSSE.slice(splitPoint)))
        controller.close()
      },
    })

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    )

    const events = []
    for await (const event of streamChat('conv-1', 'test')) {
      events.push(event)
    }

    expect(events).toHaveLength(1)
    expect(events[0]).toEqual({ type: 'TOKEN', content: 'Hi', messageId: null, sourceChunks: null })
  })

  it('skips empty data lines (SSE keepalives)', async () => {
    // A keepalive is just "\n\n" with no data: prefix
    const sseText = '\n\ndata: {"type":"TOKEN","content":"ok","messageId":null,"sourceChunks":null}\n\n'
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseText))
        controller.close()
      },
    })

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    )

    const events = []
    for await (const event of streamChat('conv-1', 'test')) {
      events.push(event)
    }

    expect(events).toHaveLength(1)
    expect(events[0].content).toBe('ok')
  })
})
