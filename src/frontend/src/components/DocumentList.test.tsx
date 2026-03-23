import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { renderWithProviders } from '../test/test-utils'
import DocumentList from './DocumentList'

describe('DocumentList', () => {
  it('shows loading state initially', () => {
    renderWithProviders(<DocumentList />)

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('shows empty state when no documents', async () => {
    server.use(
      http.get('/api/documents', () => {
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          numberOfElements: 0,
          first: true,
          last: true,
          empty: true,
        })
      }),
    )

    renderWithProviders(<DocumentList />)

    await waitFor(() => {
      expect(
        screen.getByText('No documents yet. Upload one to get started.'),
      ).toBeInTheDocument()
    })
  })

  it('renders document cards with titles and badges', async () => {
    server.use(
      http.get('/api/documents', () => {
        return HttpResponse.json({
          content: [
            {
              id: 'doc-1',
              title: 'My Insurance Policy',
              documentType: 'INSURANCE',
              inputType: 'PDF',
              status: 'READY',
              originalFilename: 'policy.pdf',
              textPreview: null,
              createdAt: '2026-03-15T10:00:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          numberOfElements: 1,
          first: true,
          last: true,
          empty: false,
        })
      }),
    )

    renderWithProviders(<DocumentList />)

    await waitFor(() => {
      expect(screen.getByText('My Insurance Policy')).toBeInTheDocument()
    })

    expect(screen.getByText('Insurance')).toBeInTheDocument()
    expect(screen.getByText('Ready')).toBeInTheDocument()
    expect(screen.getByText('PDF')).toBeInTheDocument()
  })

  it('pagination controls are disabled on first/last page', async () => {
    server.use(
      http.get('/api/documents', () => {
        return HttpResponse.json({
          content: [
            {
              id: 'doc-1',
              title: 'Doc',
              documentType: 'OTHER',
              inputType: 'TEXT',
              status: 'READY',
              originalFilename: null,
              textPreview: null,
              createdAt: '2026-03-01T12:00:00Z',
            },
          ],
          totalElements: 40,
          totalPages: 2,
          numberOfElements: 20,
          first: true,
          last: false,
          empty: false,
        })
      }),
    )

    renderWithProviders(<DocumentList />)

    await waitFor(() => {
      expect(screen.getByText('Doc')).toBeInTheDocument()
    })

    const prevButton = screen.getByRole('button', { name: 'Previous' })
    const nextButton = screen.getByRole('button', { name: 'Next' })

    // On first page: Previous disabled, Next enabled
    expect(prevButton).toBeDisabled()
    expect(nextButton).toBeEnabled()
  })

  it('sort controls change sort field', async () => {
    let lastSortParam = ''

    server.use(
      http.get('/api/documents', ({ request }) => {
        const url = new URL(request.url)
        lastSortParam = url.searchParams.get('sort') ?? ''
        return HttpResponse.json({
          content: [
            {
              id: 'doc-1',
              title: 'Doc',
              documentType: 'OTHER',
              inputType: 'TEXT',
              status: 'READY',
              originalFilename: null,
              textPreview: null,
              createdAt: '2026-03-01T12:00:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          numberOfElements: 1,
          first: true,
          last: true,
          empty: false,
        })
      }),
    )

    renderWithProviders(<DocumentList />)

    await waitFor(() => {
      expect(screen.getByText('Doc')).toBeInTheDocument()
    })

    // Default sort is createdAt,desc
    expect(lastSortParam).toBe('createdAt,desc')

    // Change sort field to title
    const sortSelect = screen.getByLabelText('Sort by')
    await userEvent.selectOptions(sortSelect, 'title')

    await waitFor(() => {
      expect(lastSortParam).toBe('title,desc')
    })
  })

  it('sort direction toggle changes direction', async () => {
    let lastSortParam = ''

    server.use(
      http.get('/api/documents', ({ request }) => {
        const url = new URL(request.url)
        lastSortParam = url.searchParams.get('sort') ?? ''
        return HttpResponse.json({
          content: [
            {
              id: 'doc-1',
              title: 'Doc',
              documentType: 'OTHER',
              inputType: 'TEXT',
              status: 'READY',
              originalFilename: null,
              textPreview: null,
              createdAt: '2026-03-01T12:00:00Z',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          numberOfElements: 1,
          first: true,
          last: true,
          empty: false,
        })
      }),
    )

    renderWithProviders(<DocumentList />)

    await waitFor(() => {
      expect(screen.getByText('Doc')).toBeInTheDocument()
    })

    // Default sort direction is desc
    expect(lastSortParam).toBe('createdAt,desc')

    // Toggle direction to asc
    const directionButton = screen.getByTitle('Descending')
    await userEvent.click(directionButton)

    await waitFor(() => {
      expect(lastSortParam).toBe('createdAt,asc')
    })
  })
})
