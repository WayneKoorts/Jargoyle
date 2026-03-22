/**
 * Truncates text to the given maximum length, appending an ellipsis
 * character if it was shortened.
 */
export function truncateWithEllipsis(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength) + '…'
}

/**
 * Picks the best display title for a document. While processing, the
 * AI-generated title hasn't been set yet, so we fall back to the
 * original filename (for PDFs) or a preview of the pasted text.
 */
export function displayTitle(doc: {
  title?: string | null
  originalFilename?: string | null
  textPreview?: string | null
}): string {
  if (doc.title) return doc.title
  if (doc.originalFilename) return truncateWithEllipsis(doc.originalFilename, 50)
  if (doc.textPreview) return truncateWithEllipsis(doc.textPreview, 50)
  return 'Untitled document'
}

/**
 * Picks the best display name for a user, falling back to their email
 * address if the display name is empty or missing.
 */
export function displayUserName(user: { displayName?: string | null; email: string }): string {
  if (user.displayName?.trim()) return user.displayName.trim()
  return user.email
}

/**
 * Formats an ISO date string into a human-readable en-GB locale string
 * (e.g. "19 Mar 2026").
 */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(iso))
}

/**
 * Formats a byte count into a human-readable size string
 * (e.g. "500 B", "1.0 KB", "10.0 MB").
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Formats an ISO date string as a human-readable relative time
 * (e.g. "just now", "3 min ago", "2 hr ago", "5 days ago").
 *
 * Used in the ConversationSidebar to show when each conversation
 * was last active without cluttering the UI with full timestamps.
 */
export function formatRelativeTime(iso: string): string {
  const diffSeconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)

  if (diffSeconds < 60) return 'just now'
  if (diffSeconds < 3600) {
    const minutes = Math.floor(diffSeconds / 60)
    return `${minutes} min ago`
  }
  if (diffSeconds < 86400) {
    const hours = Math.floor(diffSeconds / 3600)
    return `${hours} hr ago`
  }
  const days = Math.floor(diffSeconds / 86400)
  return `${days} days ago`
}
