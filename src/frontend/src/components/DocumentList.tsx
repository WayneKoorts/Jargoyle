import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useDocuments } from '../hooks/useDocuments'
import {
  DOCUMENT_TYPE_LABELS,
  STATUS_LABELS,
  INPUT_TYPE_LABELS,
  type DocumentSummary,
} from '../api/documents'

const PAGE_SIZE = 20

const SORT_OPTIONS = [
  { value: 'createdAt', label: 'Date created' },
  { value: 'title', label: 'Title' },
  { value: 'documentType', label: 'Document type' },
  { value: 'status', label: 'Status' },
] as const

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(iso))
}

const STATUS_COLOURS: Record<string, string> = {
  READY: 'bg-green-100 text-green-800',
  PROCESSING: 'bg-amber-100 text-amber-800',
  UPLOADING: 'bg-blue-100 text-blue-800',
  FAILED: 'bg-red-100 text-red-800',
}

function statusClasses(status: string): string {
  return STATUS_COLOURS[status] ?? 'bg-slate-100 text-slate-800'
}

export default function DocumentList() {
  const [page, setPage] = useState(0)
  const [sortField, setSortField] = useState('createdAt')
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc')

  const { documents, totalPages, totalElements, isFirst, isLast, isEmpty, isLoading, isError } =
    useDocuments({ page, size: PAGE_SIZE, sortField, sortDirection })

  function handleSortFieldChange(field: string) {
    setSortField(field)
    setPage(0)
  }

  function toggleSortDirection() {
    setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
    setPage(0)
  }

  if (isLoading && isEmpty) {
    return <div className="py-12 text-centre text-slate-400">Loading…</div>
  }

  if (isError) {
    return (
      <div className="py-12 text-centre">
        <p className="text-slate-500">Something went wrong loading your documents.</p>
        <button
          onClick={() => setPage(0)}
          className="mt-3 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
        >
          Retry
        </button>
      </div>
    )
  }

  if (isEmpty && !isLoading) {
    return (
      <div className="py-12 text-center text-slate-500">
        No documents yet. Upload one to get started.
      </div>
    )
  }

  return (
    <div>
      {/* Sort controls */}
      <div className="mb-4 flex items-center justify-between">
        <span className="text-sm text-slate-500">
          {totalElements} {totalElements === 1 ? 'document' : 'documents'}
        </span>
        <div className="flex items-center gap-2">
          <label htmlFor="sort-field" className="text-sm text-slate-500">
            Sort by
          </label>
          <select
            id="sort-field"
            value={sortField}
            onChange={(e) => handleSortFieldChange(e.target.value)}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-sm text-slate-700"
          >
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button
            onClick={toggleSortDirection}
            title={sortDirection === 'asc' ? 'Ascending' : 'Descending'}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-sm text-slate-700 transition-colors hover:bg-slate-50"
          >
            {sortDirection === 'asc' ? '↑' : '↓'}
          </button>
        </div>
      </div>

      {/* Document cards */}
      <ul className="space-y-3">
        {documents.map((doc: DocumentSummary) => (
          <li
            key={doc.id}
            className="rounded-lg border border-slate-200 bg-white p-4 transition-shadow hover:shadow-md"
          >
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <Link
                  to={`/documents/${doc.id}`}
                  className="text-base font-medium text-slate-900 hover:text-slate-600"
                >
                  {doc.title}
                </Link>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                    {DOCUMENT_TYPE_LABELS[doc.documentType] ?? "UNCATEGORISED"}
                  </span>
                  <span
                    className={`rounded px-2 py-0.5 text-xs font-medium ${statusClasses(doc.status)}`}
                  >
                    {STATUS_LABELS[doc.status] ?? doc.status}
                  </span>
                  <span className="text-xs text-slate-400">
                    {INPUT_TYPE_LABELS[doc.inputType] ?? doc.inputType}
                  </span>
                </div>
              </div>
              <span className="shrink-0 text-xs text-slate-400">{formatDate(doc.createdAt)}</span>
            </div>
          </li>
        ))}
      </ul>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-between">
          <button
            onClick={() => setPage((p) => p - 1)}
            disabled={isFirst}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-slate-500">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={isLast}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
