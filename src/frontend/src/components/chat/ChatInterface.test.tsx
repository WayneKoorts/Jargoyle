import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { vi } from 'vitest'
import { createTestQueryClient } from '../../test/test-utils'
import ChatInterface from './ChatInterface'
import type { Message, SuggestedQuestion } from '../../api/conversations'
import type { ReactNode } from 'react'

// Mock the hooks so we can control their return values without needing MSW
vi.mock('../../hooks/useMessages', () => ({
  useMessages: vi.fn(),
}))

vi.mock('../../hooks/useChatStream', () => ({
  useChatStream: vi.fn(),
}))

import { useMessages } from '../../hooks/useMessages'
import { useChatStream } from '../../hooks/useChatStream'

const mockUseMessages = vi.mocked(useMessages)
const mockUseChatStream = vi.mocked(useChatStream)

const sampleMessages: Message[] = [
  {
    id: 'msg-1',
    role: 'USER',
    content: 'What is this document about?',
    sourceChunks: null,
    createdAt: '2026-03-20T10:00:00Z',
  },
  {
    id: 'msg-2',
    role: 'ASSISTANT',
    content: 'This document is a utility bill.',
    sourceChunks: [{ chunkId: 'chunk-1', chunkIndex: 0, preview: 'Your monthly...' }],
    createdAt: '2026-03-20T10:01:00Z',
  },
]

const sampleSuggestions: SuggestedQuestion[] = [
  { text: 'What is the main topic?', category: 'General' },
  { text: 'Are there any costs mentioned?', category: 'Costs' },
]

function defaultStreamReturn() {
  return {
    sendMessage: vi.fn(),
    streamingContent: '',
    isStreaming: false,
    error: null,
    optimisticMessage: null,
  }
}

function defaultMessagesReturn(overrides: Partial<ReturnType<typeof useMessages>> = {}) {
  return {
    messages: [] as Message[],
    isLoading: false,
    loadMore: vi.fn(),
    hasMore: false,
    isLoadingMore: false,
    ...overrides,
  }
}

function Wrapper({ children }: { children: ReactNode }) {
  const queryClient = createTestQueryClient()
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

function renderChat(overrides?: {
  messages?: Partial<ReturnType<typeof useMessages>>
  stream?: Partial<ReturnType<typeof useChatStream>>
  suggestions?: SuggestedQuestion[]
}) {
  mockUseMessages.mockReturnValue(defaultMessagesReturn(overrides?.messages))
  mockUseChatStream.mockReturnValue({ ...defaultStreamReturn(), ...overrides?.stream })

  return render(
    <Wrapper>
      <ChatInterface
        conversationId="conv-1"
        suggestedQuestions={overrides?.suggestions ?? sampleSuggestions}
      />
    </Wrapper>,
  )
}

describe('ChatInterface', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('shows suggested questions when the conversation is empty', () => {
    renderChat()

    expect(screen.getByText('What is the main topic?')).toBeInTheDocument()
    expect(screen.getByText('Are there any costs mentioned?')).toBeInTheDocument()
  })

  it('shows a fallback message when empty with no suggestions', () => {
    renderChat({ suggestions: [] })

    expect(screen.getByText('Ask a question about this document to get started.')).toBeInTheDocument()
  })

  it('renders message bubbles for existing messages', () => {
    renderChat({ messages: { messages: sampleMessages } })

    expect(screen.getByText('What is this document about?')).toBeInTheDocument()
    expect(screen.getByText('This document is a utility bill.')).toBeInTheDocument()
  })

  it('shows optimistic user message', () => {
    const optimistic: Message = {
      id: 'optimistic-1',
      role: 'USER',
      content: 'What about fees?',
      sourceChunks: null,
      createdAt: new Date().toISOString(),
    }

    renderChat({
      messages: { messages: sampleMessages },
      stream: { optimisticMessage: optimistic },
    })

    expect(screen.getByText('What about fees?')).toBeInTheDocument()
  })

  it('shows streaming assistant bubble with content', () => {
    renderChat({
      messages: { messages: sampleMessages },
      stream: { isStreaming: true, streamingContent: 'The fees are...' },
    })

    expect(screen.getByText(/The fees are.../)).toBeInTheDocument()
  })

  it('disables input and send button while streaming', () => {
    renderChat({ stream: { isStreaming: true } })

    const textarea = screen.getByPlaceholderText('Ask a question about this document…')
    expect(textarea).toBeDisabled()

    const sendButton = screen.getByRole('button', { name: 'Send' })
    expect(sendButton).toBeDisabled()
  })

  it('calls sendMessage when the send button is clicked', async () => {
    const sendMessage = vi.fn()
    renderChat({ stream: { sendMessage } })

    const textarea = screen.getByPlaceholderText('Ask a question about this document…')
    await userEvent.type(textarea, 'My question')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    expect(sendMessage).toHaveBeenCalledWith('My question')
  })

  it('calls sendMessage on Enter key (without Shift)', async () => {
    const sendMessage = vi.fn()
    renderChat({ stream: { sendMessage } })

    const textarea = screen.getByPlaceholderText('Ask a question about this document…')
    await userEvent.type(textarea, 'My question{enter}')

    expect(sendMessage).toHaveBeenCalledWith('My question')
  })

  it('does not submit on Shift+Enter (allows newline)', async () => {
    const sendMessage = vi.fn()
    renderChat({ stream: { sendMessage } })

    const textarea = screen.getByPlaceholderText('Ask a question about this document…')
    await userEvent.type(textarea, 'Line 1{Shift>}{enter}{/Shift}Line 2')

    expect(sendMessage).not.toHaveBeenCalled()
  })

  it('clears the input after sending', async () => {
    const sendMessage = vi.fn()
    renderChat({ stream: { sendMessage } })

    const textarea = screen.getByPlaceholderText('Ask a question about this document…')
    await userEvent.type(textarea, 'My question')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    expect(textarea).toHaveValue('')
  })

  it('shows error banner when error is present', () => {
    renderChat({ stream: { error: 'Something went wrong.' } })

    expect(screen.getByText('Something went wrong.')).toBeInTheDocument()
  })

  it('shows "Load earlier messages" button when hasMore is true', () => {
    renderChat({
      messages: { messages: sampleMessages, hasMore: true },
    })

    expect(screen.getByRole('button', { name: 'Load earlier messages' })).toBeInTheDocument()
  })

  it('calls loadMore when "Load earlier messages" is clicked', async () => {
    const loadMore = vi.fn()
    renderChat({
      messages: { messages: sampleMessages, hasMore: true, loadMore },
    })

    await userEvent.click(screen.getByRole('button', { name: 'Load earlier messages' }))
    expect(loadMore).toHaveBeenCalledOnce()
  })

  it('clicking a suggested question triggers sendMessage', async () => {
    const sendMessage = vi.fn()
    renderChat({ stream: { sendMessage } })

    await userEvent.click(screen.getByRole('button', { name: 'What is the main topic?' }))
    expect(sendMessage).toHaveBeenCalledWith('What is the main topic?')
  })

  it('shows loading state', () => {
    renderChat({ messages: { isLoading: true } })

    expect(screen.getByText('Loading messages…')).toBeInTheDocument()
  })

  it('shows thinking indicator when streaming but no content yet', () => {
    renderChat({
      messages: { messages: sampleMessages },
      stream: { isStreaming: true, streamingContent: '' },
    })

    expect(screen.getByRole('status', { name: 'AI is thinking' })).toBeInTheDocument()
  })

  it('does not show thinking indicator once content starts streaming', () => {
    renderChat({
      messages: { messages: sampleMessages },
      stream: { isStreaming: true, streamingContent: 'Starting...' },
    })

    expect(screen.queryByRole('status', { name: 'AI is thinking' })).not.toBeInTheDocument()
  })
})
