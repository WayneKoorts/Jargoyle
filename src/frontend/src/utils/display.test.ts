import { displayTitle, truncateWithEllipsis, formatDate, formatFileSize } from './display'
import type { DocumentSummary } from '../api/documents'

/** Helper to build a minimal DocumentSummary with overrides */
function makeDoc(overrides: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'doc-1',
    title: null,
    documentType: 'OTHER',
    inputType: 'TEXT',
    status: 'READY',
    originalFilename: null,
    textPreview: null,
    createdAt: '2026-03-19T12:00:00Z',
    ...overrides,
  }
}

describe('displayTitle', () => {
  it('returns the title when the document has one', () => {
    expect(displayTitle(makeDoc({ title: 'My Document' }))).toBe('My Document')
  })

  it('falls back to truncated originalFilename when there is no title', () => {
    const longName = 'a'.repeat(60) + '.pdf'
    const result = displayTitle(makeDoc({ originalFilename: longName }))
    expect(result).toHaveLength(51) // 50 chars + ellipsis
    expect(result.endsWith('…')).toBe(true)
  })

  it('falls back to truncated textPreview when there is no title or filename', () => {
    const preview = 'This is a preview of some document text'
    expect(displayTitle(makeDoc({ textPreview: preview }))).toBe(preview)
  })

  it('returns "Untitled document" when nothing is available', () => {
    expect(displayTitle(makeDoc())).toBe('Untitled document')
  })
})

describe('truncateWithEllipsis', () => {
  it('returns text unchanged when shorter than maxLength', () => {
    expect(truncateWithEllipsis('short', 10)).toBe('short')
  })

  it('returns text unchanged when exactly maxLength', () => {
    expect(truncateWithEllipsis('exact', 5)).toBe('exact')
  })

  it('truncates and adds ellipsis when text exceeds maxLength', () => {
    const result = truncateWithEllipsis('hello world', 5)
    expect(result).toBe('hello…')
  })
})

describe('formatFileSize', () => {
  it('formats bytes', () => {
    expect(formatFileSize(500)).toBe('500 B')
  })

  it('formats zero bytes', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('formats kilobytes', () => {
    expect(formatFileSize(1024)).toBe('1.0 KB')
    expect(formatFileSize(1536)).toBe('1.5 KB')
  })

  it('formats megabytes', () => {
    expect(formatFileSize(10 * 1024 * 1024)).toBe('10.0 MB')
    expect(formatFileSize(5.5 * 1024 * 1024)).toBe('5.5 MB')
  })
})

describe('formatDate', () => {
  it('formats an ISO string in en-GB locale', () => {
    const result = formatDate('2026-03-19T12:00:00Z')
    // Node ICU data can vary, so match the general pattern
    expect(result).toMatch(/\d{1,2} \w{3} \d{4}/)
  })

  it('handles different dates correctly', () => {
    const result = formatDate('2025-01-01T00:00:00Z')
    expect(result).toMatch(/\d{1,2} \w{3} \d{4}/)
  })
})
