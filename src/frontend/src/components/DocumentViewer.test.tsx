import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { renderWithProviders } from '../test/test-utils'
import DocumentViewer from './DocumentViewer'

function renderViewer(props?: Partial<{ documentId: string; originalFilename: string | null }>) {
  return renderWithProviders(
    <DocumentViewer
      documentId={props?.documentId ?? 'doc-1'}
      originalFilename={props?.originalFilename ?? 'test.pdf'}
    />,
  )
}

describe('DocumentViewer', () => {
  it('renders collapsed accordion header initially', () => {
    renderViewer()

    expect(screen.getByText('Original Document')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /original document/i })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
    // Body should not be visible
    expect(screen.queryByText('Loading document…')).not.toBeInTheDocument()
  })

  it('does not fetch content until expanded', () => {
    let fetchCount = 0
    server.use(
      http.get('/api/documents/:id/original', () => {
        fetchCount++
        return HttpResponse.json({ url: null, text: 'Content', inputType: 'TEXT' })
      }),
    )

    renderViewer()
    expect(fetchCount).toBe(0)
  })

  it('shows loading state after expanding', async () => {
    const user = userEvent.setup()

    // Delay the response so we can observe the loading state
    server.use(
      http.get('/api/documents/:id/original', async () => {
        await new Promise((r) => setTimeout(r, 100))
        return HttpResponse.json({ url: null, text: 'Content', inputType: 'TEXT' })
      }),
    )

    renderViewer()
    await user.click(screen.getByRole('button', { name: /original document/i }))

    expect(screen.getByText('Loading document…')).toBeInTheDocument()
  })

  it('renders text content for TEXT documents', async () => {
    const user = userEvent.setup()

    server.use(
      http.get('/api/documents/:id/original', () => {
        return HttpResponse.json({
          url: null,
          text: 'Hello, world! This is the original text.',
          inputType: 'TEXT',
        })
      }),
    )

    renderViewer()
    await user.click(screen.getByRole('button', { name: /original document/i }))

    await waitFor(() => {
      expect(screen.getByText('Hello, world! This is the original text.')).toBeInTheDocument()
    })

    // Should render in a <pre> element
    const preElement = screen.getByText('Hello, world! This is the original text.')
    expect(preElement.tagName).toBe('PRE')
  })

  it('renders iframe for PDF documents', async () => {
    const user = userEvent.setup()

    server.use(
      http.get('/api/documents/:id/original', () => {
        return HttpResponse.json({
          url: 'https://s3.example.com/presigned-pdf-url',
          text: null,
          inputType: 'PDF',
        })
      }),
    )

    renderViewer({ originalFilename: 'contract.pdf' })
    await user.click(screen.getByRole('button', { name: /original document/i }))

    await waitFor(() => {
      const iframe = document.querySelector('iframe')
      expect(iframe).toBeInTheDocument()
      expect(iframe).toHaveAttribute('src', 'https://s3.example.com/presigned-pdf-url')
      expect(iframe).toHaveAttribute('title', 'contract.pdf')
    })
  })

  it('renders image for IMAGE documents', async () => {
    const user = userEvent.setup()

    server.use(
      http.get('/api/documents/:id/original', () => {
        return HttpResponse.json({
          url: 'https://s3.example.com/presigned-image-url',
          text: null,
          inputType: 'IMAGE',
        })
      }),
    )

    renderViewer({ originalFilename: 'receipt.png' })
    await user.click(screen.getByRole('button', { name: /original document/i }))

    await waitFor(() => {
      const img = screen.getByAltText('receipt.png')
      expect(img).toBeInTheDocument()
      expect(img).toHaveAttribute('src', 'https://s3.example.com/presigned-image-url')
    })
  })

  it('shows error state with retry on failure', async () => {
    const user = userEvent.setup()

    server.use(
      http.get('/api/documents/:id/original', () => {
        return new HttpResponse(null, { status: 500, statusText: 'Internal Server Error' })
      }),
    )

    renderViewer()
    await user.click(screen.getByRole('button', { name: /original document/i }))

    await waitFor(() => {
      expect(screen.getByText(/failed to load|api error/i)).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })

  it('shows "Open in new tab" link after content loads', async () => {
    const user = userEvent.setup()

    server.use(
      http.get('/api/documents/:id/original', () => {
        return HttpResponse.json({
          url: 'https://s3.example.com/presigned-url',
          text: null,
          inputType: 'PDF',
        })
      }),
    )

    renderViewer()
    await user.click(screen.getByRole('button', { name: /original document/i }))

    await waitFor(() => {
      const link = screen.getByLabelText('Open original in new tab')
      expect(link).toBeInTheDocument()
      expect(link).toHaveAttribute('href', 'https://s3.example.com/presigned-url')
      expect(link).toHaveAttribute('target', '_blank')
    })
  })

  it('collapsing and re-expanding does not re-fetch', async () => {
    const user = userEvent.setup()
    let fetchCount = 0

    server.use(
      http.get('/api/documents/:id/original', () => {
        fetchCount++
        return HttpResponse.json({ url: null, text: 'Cached content', inputType: 'TEXT' })
      }),
    )

    renderViewer()
    const toggle = screen.getByRole('button', { name: /original document/i })

    // Expand — triggers fetch
    await user.click(toggle)
    await waitFor(() => {
      expect(screen.getByText('Cached content')).toBeInTheDocument()
    })
    expect(fetchCount).toBe(1)

    // Collapse
    await user.click(toggle)
    expect(screen.queryByText('Cached content')).not.toBeInTheDocument()

    // Re-expand — should use cached data, no additional fetch
    await user.click(toggle)
    await waitFor(() => {
      expect(screen.getByText('Cached content')).toBeInTheDocument()
    })
    expect(fetchCount).toBe(1)
  })
})
