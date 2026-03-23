import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import type { UserProfile } from '../api/auth'
import DocumentDetailsPage from './DocumentDetailsPage'
import { server } from '../test/msw-server'
import { renderWithProviders } from '../test/test-utils'
import { useDocumentStatus } from '../hooks/useDocumentStatus'
import { displayTitle } from '../utils/display'

vi.mock('../hooks/useDocumentStatus', () => ({
  useDocumentStatus: vi.fn(),
}))

// Mock the chat hooks to isolate page-level behaviour from hook internals.
vi.mock('../hooks/useMessages', () => ({
  useMessages: vi.fn().mockReturnValue({
    messages: [],
    isLoading: false,
    loadMore: vi.fn(),
    hasMore: false,
    isLoadingMore: false,
  }),
}))

vi.mock('../hooks/useChatStream', () => ({
  useChatStream: vi.fn().mockReturnValue({
    sendMessage: vi.fn(),
    streamingContent: '',
    isStreaming: false,
    error: null,
    optimisticMessage: null,
  }),
}))

const mockUseDocumentStatus = vi.mocked(useDocumentStatus)

const user: UserProfile = {
  id: 'user-1',
  email: 'test@example.com',
  displayName: 'Test User',
  oauthProvider: 'google',
  role: 'USER',
  enabled: true,
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route
        path="/documents/:id"
        element={<DocumentDetailsPage user={user} onLogout={vi.fn()} />}
      />
    </Routes>,
    {
      routerProps: {
        initialEntries: ['/documents/doc-1'],
      },
    },
  )
}

/** Helper to set up a READY document with summary data. */
function useReadyDocument() {
  mockUseDocumentStatus.mockReturnValue({
    status: null,
    step: null,
    errorMessage: null,
    isComplete: false,
    isFailed: false,
  })

  server.use(
    http.get('/api/documents/:id', ({ params }) => {
      return HttpResponse.json({
        id: params.id,
        title: 'My Electricity Bill',
        documentType: 'BILL',
        inputType: 'PDF',
        originalFilename: 'bill.pdf',
        status: 'READY',
        errorMessage: null,
        summary: {
          plainSummary: 'This is your monthly electricity bill.',
          keyFacts: '{"amounts":[{"label":"Total","value":"£150","context":"monthly"}],"dates":[],"parties":[]}',
          flaggedTerms: '[{"term":"kWh","definition":"Kilowatt-hours, a unit of energy"}]',
        },
        createdAt: '2026-03-01T12:00:00Z',
      })
    }),
  )
}

describe('DocumentDetailsPage', () => {
  beforeEach(() => {
    mockUseDocumentStatus.mockReturnValue({
      status: 'PROCESSING',
      step: 'Extracting text',
      errorMessage: null,
      isComplete: false,
      isFailed: false,
    })
  })

  afterEach(() => {
    mockUseDocumentStatus.mockReset()
  })

  // --- Processing state tests ---

  it('shows the derived temporary title and spinner while processing', async () => {
    const originalFilename = 'extremely-long-document-name-that-needs-truncating.pdf'

    server.use(
      http.get('/api/documents/:id', ({ params }) => {
        return HttpResponse.json({
          id: params.id,
          title: null,
          documentType: 'BILL',
          inputType: 'PDF',
          originalFilename,
          status: 'PROCESSING',
          errorMessage: null,
          summary: null,
          createdAt: '2026-03-01T12:00:00Z',
        })
      }),
    )

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', {
          level: 2,
          name: displayTitle({ title: null, originalFilename }),
        }),
      ).toBeInTheDocument()
    })

    expect(screen.getByRole('status', { name: 'Document processing' })).toBeInTheDocument()
    expect(screen.getByText('Extracting text')).toBeInTheDocument()
  })

  it('updates to the final title and removes the spinner when processing completes', async () => {
    let requestCount = 0

    mockUseDocumentStatus.mockImplementation((documentId) => ({
      status: documentId ? 'READY' : null,
      step: null,
      errorMessage: null,
      isComplete: documentId != null,
      isFailed: false,
    }))

    server.use(
      http.get('/api/documents/:id', ({ params }) => {
        requestCount += 1

        if (requestCount === 1) {
          return HttpResponse.json({
            id: params.id,
            title: null,
            documentType: 'BILL',
            inputType: 'PDF',
            originalFilename: 'processing-title.pdf',
            status: 'PROCESSING',
            errorMessage: null,
            summary: null,
            createdAt: '2026-03-01T12:00:00Z',
          })
        }

        return HttpResponse.json({
          id: params.id,
          title: 'Final AI title',
          documentType: 'BILL',
          inputType: 'PDF',
          originalFilename: 'processing-title.pdf',
          status: 'READY',
          errorMessage: null,
          summary: {
            plainSummary: 'Done',
            keyFacts: '{"amounts":[],"dates":[],"parties":[]}',
            flaggedTerms: '[]',
          },
          createdAt: '2026-03-01T12:00:00Z',
        })
      }),
    )

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'Final AI title' })).toBeInTheDocument()
    })

    expect(screen.queryByRole('status', { name: 'Document processing' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 2, name: 'processing-title.pdf' })).not.toBeInTheDocument()
  })

  // --- READY state / collapsible chat tests ---

  it('shows summary content when document is READY', async () => {
    useReadyDocument()
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'My Electricity Bill' })).toBeInTheDocument()
    })

    expect(screen.getByText('This is your monthly electricity bill.')).toBeInTheDocument()
    expect(screen.getByText('kWh')).toBeInTheDocument()
    expect(screen.getByText('Kilowatt-hours, a unit of energy')).toBeInTheDocument()
  })

  it('shows the "Ask AI" button when document is READY', async () => {
    useReadyDocument()
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'My Electricity Bill' })).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: /Ask AI about this document/i })).toBeInTheDocument()
  })

  it('opens the chat drawer when "Ask AI" button is clicked', async () => {
    useReadyDocument()
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'My Electricity Bill' })).toBeInTheDocument()
    })

    await userEvent.click(screen.getByRole('button', { name: /Ask AI about this document/i }))

    // Button label toggles to "Hide chat" when drawer is open
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Hide chat/i })).toBeInTheDocument()
    })
  })

  it('toggles the chat drawer closed when "Hide chat" is clicked', async () => {
    useReadyDocument()
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'My Electricity Bill' })).toBeInTheDocument()
    })

    // Open the chat
    await userEvent.click(screen.getByRole('button', { name: /Ask AI about this document/i }))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Hide chat/i })).toBeInTheDocument()
    })

    // Close via the same toggle button
    await userEvent.click(screen.getByRole('button', { name: /Hide chat/i }))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Ask AI about this document/i })).toBeInTheDocument()
    })
  })

  it('shows key facts in the summary panel', async () => {
    useReadyDocument()
    renderPage()

    await waitFor(() => {
      expect(screen.getByText('£150')).toBeInTheDocument()
    })

    expect(screen.getByText('Total')).toBeInTheDocument()
  })
})
