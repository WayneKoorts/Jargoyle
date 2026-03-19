import { apiClient, apiFormData } from './client'

// Mirrors the backend DocumentListResponse record
export interface DocumentSummary {
  id: string
  title: string | null
  documentType: string
  inputType: string
  status: string
  originalFilename: string | null
  textPreview: string | null
  createdAt: string
}

// Generic Spring Data Page shape — reusable for any paginated endpoint
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  numberOfElements: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface DocumentListParams {
  page: number
  size: number
  sortField: string
  sortDirection: 'asc' | 'desc'
}

export const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  BILL: 'Bill',
  INSURANCE: 'Insurance',
  RENTAL: 'Rental',
  MORTGAGE: 'Mortgage',
  BANK_TERMS: 'Bank Terms',
  CONTRACT: 'Contract',
  GOVERNMENT: 'Government',
  MEDICAL: 'Medical',
  TAX: 'Tax',
  OTHER: 'Other',
}

export const STATUS_LABELS: Record<string, string> = {
  UPLOADING: 'Uploading',
  PROCESSING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

export const INPUT_TYPE_LABELS: Record<string, string> = {
  PDF: 'PDF',
  IMAGE: 'Image',
  TEXT: 'Text',
}

// Mirrors the backend DocumentSummaryResponse record
export interface DocumentSummaryResponse {
  plainSummary: string
  keyFacts: string
  flaggedTerms: string
}

// Parsed structures for the JSON strings inside DocumentSummaryResponse
export interface KeyFact {
  label: string
  value: string
  context: string
}

export interface KeyFacts {
  amounts: KeyFact[]
  dates: KeyFact[]
  parties: KeyFact[]
}

export interface FlaggedTerm {
  term: string
  definition: string
}

/**
 * Safely parses the keyFacts JSON string from the backend.
 * Returns empty groups if the input is null, undefined, or malformed.
 */
export function parseKeyFacts(json: string | null | undefined): KeyFacts {
  const empty: KeyFacts = { amounts: [], dates: [], parties: [] }
  if (!json) return empty
  try {
    const parsed = JSON.parse(json)
    return {
      amounts: Array.isArray(parsed.amounts) ? parsed.amounts : [],
      dates: Array.isArray(parsed.dates) ? parsed.dates : [],
      parties: Array.isArray(parsed.parties) ? parsed.parties : [],
    }
  } catch {
    return empty
  }
}

/**
 * Safely parses the flaggedTerms JSON string from the backend.
 * Returns an empty array if the input is null, undefined, or malformed.
 */
export function parseFlaggedTerms(json: string | null | undefined): FlaggedTerm[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

// Mirrors the backend DocumentResponse record (returned from upload and detail)
export interface DocumentResponse {
  id: string
  title: string
  documentType: string
  inputType: string
  originalFilename: string | null
  status: string
  errorMessage: string | null
  summary: DocumentSummaryResponse | null
  createdAt: string
}

// Mirrors the backend ProcessingStatusEvent record (sent over SSE)
export interface ProcessingStatusEvent {
  status: 'PROCESSING' | 'READY' | 'FAILED'
  step: string
  errorMessage: string | null
}

// Validation constants matching backend limits
export const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
export const MAX_TEXT_LENGTH = 100_000
export const ACCEPTED_FILE_TYPES = '.pdf'

export function uploadFile(file: File, fileName?: string): Promise<DocumentResponse> {
  const formData = new FormData()
  formData.append('file', file)
  if (fileName) {
    formData.append('fileName', fileName)
  }
  return apiFormData<DocumentResponse>('/documents', formData)
}

export function uploadText(text: string): Promise<DocumentResponse> {
  const formData = new FormData()
  formData.append('text', text)
  return apiFormData<DocumentResponse>('/documents', formData)
}

export function fetchDocuments(params: DocumentListParams): Promise<Page<DocumentSummary>> {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
    sort: `${params.sortField},${params.sortDirection}`,
  })

  return apiClient<Page<DocumentSummary>>(`/documents?${query}`)
}

export function fetchDocument(id: string): Promise<DocumentResponse> {
  return apiClient<DocumentResponse>(`/documents/${id}`)
}

export function deleteDocument(id: string): Promise<void> {
  return apiClient<void>(`/documents/${id}`, { method: 'DELETE' })
}
