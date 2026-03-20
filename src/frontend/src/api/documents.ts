import { apiClient, apiFormData } from './client'
import type { Page } from './types'

// Re-export so existing imports from this module still work
export type { Page } from './types'

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
  PENDING_UPLOAD: 'Waiting for upload',
  UPLOADING: 'Uploading',
  QUEUED: 'Queued',
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
  } catch (e) {
    console.warn('Failed to parse keyFacts JSON — displaying empty results:', e)
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
  } catch (e) {
    console.warn('Failed to parse flaggedTerms JSON — displaying empty results:', e)
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

export interface DocumentUploadTargetResponse {
  url: string
  method: 'PUT' | 'POST'
}

export interface DocumentUploadSessionResponse {
  document: DocumentResponse
  uploadTarget: DocumentUploadTargetResponse | null
}

// Mirrors the backend ProcessingStatusEvent record (sent over SSE)
export interface ProcessingStatusEvent {
  status: 'PENDING_UPLOAD' | 'UPLOADING' | 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED'
  step: string
  errorMessage: string | null
}

// Validation constants matching backend limits
export const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
export const MAX_TEXT_LENGTH = 100_000
export const ACCEPTED_FILE_TYPES = '.pdf'

export function createDocumentUploadSession(params: {
  inputType: 'PDF' | 'TEXT'
  fileName?: string
  text?: string
}): Promise<DocumentUploadSessionResponse> {
  return apiClient<DocumentUploadSessionResponse>('/documents/uploads', {
    method: 'POST',
    body: {
      inputType: params.inputType,
      originalFilename: params.fileName ?? null,
      text: params.text ?? null,
    },
  })
}

export async function uploadDocumentContent(target: DocumentUploadTargetResponse, file: File): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)

  if (/^https?:\/\//.test(target.url)) {
    const response = await fetch(target.url, {
      method: target.method,
      body: file,
    })

    if (!response.ok) {
      const errorBody = await response.text().catch(() => '')
      throw new Error(errorBody || `Upload error: ${response.status} ${response.statusText}`)
    }

    return
  }

  await apiFormData<void>(target.url, formData, { method: target.method })
}

export function finaliseDocumentUpload(documentId: string): Promise<DocumentResponse> {
  return apiClient<DocumentResponse>(`/documents/${documentId}/finalise`, {
    method: 'POST',
  })
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
