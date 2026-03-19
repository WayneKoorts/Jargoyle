import { useQuery } from '@tanstack/react-query'
import { fetchDocument, type DocumentResponse } from '../api/documents'

/**
 * React Query hook for fetching a single document by ID.
 *
 * `enabled: !!id` prevents the query from firing when the route param
 * is undefined (e.g. during initial render before React Router resolves).
 * The `refetch` function is exposed so callers can trigger a refresh
 * after SSE signals that processing is complete.
 */
export function useDocument(id: string | undefined) {
  const { data, isLoading, isError, refetch } = useQuery<DocumentResponse>({
    queryKey: ['document', id],
    queryFn: () => fetchDocument(id!),
    enabled: !!id,
  })

  return {
    document: data ?? null,
    isLoading,
    isError,
    refetch,
  }
}
