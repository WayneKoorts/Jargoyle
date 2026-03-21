import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { vi } from 'vitest'
import type { UserProfile } from '../api/auth'
import DocumentDetailsPage from './DocumentDetailsPage'
import { server } from '../test/msw-server'
import { renderWithProviders } from '../test/test-utils'
import { useDocumentStatus } from '../hooks/useDocumentStatus'
import { displayTitle } from '../utils/display'

vi.mock('../hooks/useDocumentStatus', () => ({
  useDocumentStatus: vi.fn(),
}))

const mockUseDocumentStatus = vi.mocked(useDocumentStatus)

const user: UserProfile = {
  id: 'user-1',
  email: 'test@example.com',
  displayName: 'Test User',
  oauthProvider: 'google',
  role: 'USER',
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
})
