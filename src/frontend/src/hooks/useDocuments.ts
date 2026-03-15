import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { fetchDocuments, type DocumentListParams } from '../api/documents'

/**
 * Fetches a paginated, sorted list of the current user's documents.
 *
 * keepPreviousData prevents the list from flashing empty while the next
 * page loads — the old content stays visible until the new data arrives.
 */
export function useDocuments(params: DocumentListParams) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['documents', params],
    queryFn: () => fetchDocuments(params),
    placeholderData: keepPreviousData,
  })

  return {
    documents: data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalElements: data?.totalElements ?? 0,
    isEmpty: data?.empty ?? true,
    isFirst: data?.first ?? true,
    isLast: data?.last ?? true,
    isLoading,
    isError,
  }
}
