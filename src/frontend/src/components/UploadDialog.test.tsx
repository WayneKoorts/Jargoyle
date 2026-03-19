import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/test-utils'
import UploadDialog from './UploadDialog'

// jsdom doesn't implement HTMLDialogElement.showModal() or close().
// Mock them on the prototype so the component can call them.
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  })
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.removeAttribute('open')
  })
})

describe('UploadDialog', () => {
  it('shows "Upload Document" heading in input phase', () => {
    renderWithProviders(<UploadDialog open={true} onClose={vi.fn()} />)

    expect(screen.getByText('Upload Document')).toBeInTheDocument()
  })

  it('tab switching shows file/text content', async () => {
    renderWithProviders(<UploadDialog open={true} onClose={vi.fn()} />)

    // Default tab is file — should show file upload area
    expect(screen.getByText(/drag and drop a pdf/i)).toBeInTheDocument()

    // Switch to text tab
    await userEvent.click(screen.getByRole('button', { name: 'Paste Text' }))

    // Should now show textarea
    expect(screen.getByPlaceholderText('Paste your document text here...')).toBeInTheDocument()
  })

  it('file validation rejects non-PDF', async () => {
    renderWithProviders(<UploadDialog open={true} onClose={vi.fn()} />)

    // Create a non-PDF file and fire the change event directly,
    // because userEvent.upload on a hidden input can be unreliable in jsdom
    const txtFile = new File(['hello'], 'document.txt', { type: 'text/plain' })
    const input = document.querySelector('input[type="file"]') as HTMLInputElement

    // Manually set files and fire change — mirrors what the browser does
    Object.defineProperty(input, 'files', { value: [txtFile], configurable: true })
    input.dispatchEvent(new Event('change', { bubbles: true }))

    await waitFor(() => {
      expect(screen.getByText('Only PDF files are accepted.')).toBeInTheDocument()
    })
  })

  it('file validation rejects oversized files', async () => {
    renderWithProviders(<UploadDialog open={true} onClose={vi.fn()} />)

    // Create an oversized PDF (>10MB)
    const bigContent = new ArrayBuffer(11 * 1024 * 1024)
    const bigFile = new File([bigContent], 'large.pdf', { type: 'application/pdf' })
    const input = document.querySelector('input[type="file"]') as HTMLInputElement

    await userEvent.upload(input, bigFile)

    await waitFor(() => {
      expect(screen.getByText(/file is too large/i)).toBeInTheDocument()
    })
  })

  it('character counter updates as user types', async () => {
    renderWithProviders(<UploadDialog open={true} onClose={vi.fn()} />)

    // Switch to text tab
    await userEvent.click(screen.getByRole('button', { name: 'Paste Text' }))

    const textarea = screen.getByPlaceholderText('Paste your document text here...')
    await userEvent.type(textarea, 'Hello')

    // Character counter should show "5 / 100,000 characters"
    expect(screen.getByText(/5 \/ 100,000 characters/)).toBeInTheDocument()
  })

  it('upload button is disabled when nothing is selected', () => {
    renderWithProviders(<UploadDialog open={true} onClose={vi.fn()} />)

    const uploadButton = screen.getByRole('button', { name: 'Upload' })
    expect(uploadButton).toBeDisabled()
  })
})
