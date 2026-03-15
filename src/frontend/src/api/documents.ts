import { apiClient } from './client'

// Mirrors the backend DocumentListResponse record
export interface DocumentSummary {
  id: string
  title: string
  documentType: string
  inputType: string
  status: string
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

export function fetchDocuments(params: DocumentListParams): Promise<Page<DocumentSummary>> {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
    sort: `${params.sortField},${params.sortDirection}`,
  })

  return apiClient<Page<DocumentSummary>>(`/documents?${query}`)
}
