import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { vi } from 'vitest'
import { createTestQueryClient } from '../../test/test-utils'
import ConversationSidebar from './ConversationSidebar'
import type { ReactNode } from 'react'

vi.mock('../../hooks/useConversations', () => ({
  useConversations: vi.fn(),
}))

import { useConversations } from '../../hooks/useConversations'

const mockUseConversations = vi.mocked(useConversations)

const sampleConversations = [
  {
    id: 'conv-1',
    documentId: 'doc-1',
    title: 'About my bill',
    messageCount: 4,
    createdAt: '2026-03-20T10:00:00Z',
    lastMessageAt: '2026-03-20T10:05:00Z',
  },
  {
    id: 'conv-2',
    documentId: 'doc-1',
    title: null,
    messageCount: 0,
    createdAt: '2026-03-20T11:00:00Z',
    lastMessageAt: '2026-03-20T11:00:00Z',
  },
]

function Wrapper({ children }: { children: ReactNode }) {
  const queryClient = createTestQueryClient()
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

function renderSidebar(overrides?: {
  activeId?: string | null
  onSelect?: () => void
  onNew?: () => void
}) {
  mockUseConversations.mockReturnValue({
    conversations: sampleConversations,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  })

  const onSelect = overrides?.onSelect ?? vi.fn()
  const onNew = overrides?.onNew ?? vi.fn()

  return render(
    <Wrapper>
      <ConversationSidebar
        documentId="doc-1"
        activeConversationId={overrides?.activeId ?? 'conv-1'}
        onSelect={onSelect}
        onNewConversation={onNew}
      />
    </Wrapper>,
  )
}

describe('ConversationSidebar', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders a dropdown with all conversations', () => {
    renderSidebar()

    const select = screen.getByRole('combobox', { name: 'Select conversation' })
    expect(select).toBeInTheDocument()

    const options = screen.getAllByRole('option')
    expect(options).toHaveLength(2)
  })

  it('shows "New conversation" for conversations with null title', () => {
    renderSidebar()

    expect(screen.getByRole('option', { name: 'New conversation' })).toBeInTheDocument()
  })

  it('shows conversation titles in the dropdown', () => {
    renderSidebar()

    expect(screen.getByRole('option', { name: 'About my bill' })).toBeInTheDocument()
  })

  it('calls onSelect when a different conversation is chosen', async () => {
    const onSelect = vi.fn()
    renderSidebar({ activeId: 'conv-1', onSelect })

    const select = screen.getByRole('combobox', { name: 'Select conversation' })
    await userEvent.selectOptions(select, 'conv-2')

    expect(onSelect).toHaveBeenCalledWith('conv-2')
  })

  it('renders a round new-conversation button with plus icon', () => {
    renderSidebar()

    const button = screen.getByRole('button', { name: 'New conversation' })
    expect(button).toBeInTheDocument()
    expect(button.className).toContain('rounded-full')
  })

  it('calls onNewConversation when the plus button is clicked', async () => {
    const onNew = vi.fn()
    renderSidebar({ onNew })

    await userEvent.click(screen.getByRole('button', { name: 'New conversation' }))
    expect(onNew).toHaveBeenCalledOnce()
  })

  it('disables the dropdown while loading', () => {
    mockUseConversations.mockReturnValue({
      conversations: [],
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
    })

    render(
      <Wrapper>
        <ConversationSidebar
          documentId="doc-1"
          activeConversationId={null}
          onSelect={vi.fn()}
          onNewConversation={vi.fn()}
        />
      </Wrapper>,
    )

    expect(screen.getByRole('combobox', { name: 'Select conversation' })).toBeDisabled()
    expect(screen.getByRole('option', { name: 'Loading…' })).toBeInTheDocument()
  })
})
