import { render, screen } from '@testing-library/react'
import MessageBubble from './MessageBubble'
import type { Message } from '../../api/conversations'

const userMessage: Message = {
  id: 'msg-1',
  role: 'USER',
  content: 'What is this document about?',
  sourceChunks: null,
  createdAt: '2026-03-20T10:00:00Z',
}

const assistantMessage: Message = {
  id: 'msg-2',
  role: 'ASSISTANT',
  content: 'This document is a utility bill.',
  sourceChunks: [
    { chunkId: 'chunk-1', chunkIndex: 0, preview: 'Your monthly electricity...' },
  ],
  createdAt: '2026-03-20T10:01:00Z',
}

describe('MessageBubble', () => {
  it('renders user message content', () => {
    render(<MessageBubble message={userMessage} />)
    expect(screen.getByText('What is this document about?')).toBeInTheDocument()
  })

  it('renders assistant message content', () => {
    render(<MessageBubble message={assistantMessage} />)
    expect(screen.getByText('This document is a utility bill.')).toBeInTheDocument()
  })

  it('shows pulsing cursor when streaming', () => {
    const streamingMsg: Message = { ...assistantMessage, sourceChunks: null }
    const { container } = render(<MessageBubble message={streamingMsg} isStreaming />)

    const cursor = container.querySelector('.animate-pulse')
    expect(cursor).toBeInTheDocument()
    expect(cursor?.textContent).toBe('▊')
  })

  it('does not show cursor when not streaming', () => {
    const { container } = render(<MessageBubble message={assistantMessage} />)
    expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument()
  })

  it('shows animated thinking dots when isThinking is true', () => {
    const thinkingMsg: Message = { ...assistantMessage, content: '', sourceChunks: null }
    render(<MessageBubble message={thinkingMsg} isThinking />)

    expect(screen.getByRole('status', { name: 'AI is thinking' })).toBeInTheDocument()
  })

  it('does not show message content when thinking', () => {
    const thinkingMsg: Message = { ...assistantMessage, content: '', sourceChunks: null }
    render(<MessageBubble message={thinkingMsg} isThinking />)

    expect(screen.queryByText('This document is a utility bill.')).not.toBeInTheDocument()
  })
})
