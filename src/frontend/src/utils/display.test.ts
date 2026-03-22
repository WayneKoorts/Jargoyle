import { displayTitle, displayUserName, truncateWithEllipsis, formatDate, formatFileSize, formatRelativeTime } from './display'
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

describe('displayUserName', () => {
  it('returns the display name when present', () => {
    expect(displayUserName({ displayName: 'Alice', email: 'alice@example.com' })).toBe('Alice')
  })

  it('falls back to email when displayName is null', () => {
    expect(displayUserName({ displayName: null, email: 'alice@example.com' })).toBe('alice@example.com')
  })

  it('falls back to email when displayName is undefined', () => {
    expect(displayUserName({ email: 'alice@example.com' })).toBe('alice@example.com')
  })

  it('falls back to email when displayName is empty string', () => {
    expect(displayUserName({ displayName: '', email: 'alice@example.com' })).toBe('alice@example.com')
  })

  it('falls back to email when displayName is only whitespace', () => {
    expect(displayUserName({ displayName: '   ', email: 'alice@example.com' })).toBe('alice@example.com')
  })

  it('trims whitespace from display name', () => {
    expect(displayUserName({ displayName: '  Alice  ', email: 'alice@example.com' })).toBe('Alice')
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

describe('formatRelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-22T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns "just now" for timestamps less than a minute ago', () => {
    expect(formatRelativeTime('2026-03-22T11:59:30Z')).toBe('just now')
  })

  it('returns minutes for timestamps less than an hour ago', () => {
    expect(formatRelativeTime('2026-03-22T11:55:00Z')).toBe('5 min ago')
  })

  it('returns hours for timestamps less than a day ago', () => {
    expect(formatRelativeTime('2026-03-22T09:00:00Z')).toBe('3 hr ago')
  })

  it('returns days for timestamps more than a day ago', () => {
    expect(formatRelativeTime('2026-03-17T12:00:00Z')).toBe('5 days ago')
  })
})
