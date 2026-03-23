import { useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  fetchDocumentContentLocation,
  type DocumentContentLocationResponse,
} from '../api/documents'

/**
 * React Query hook for lazy-loading the original document content.
 *
 * The query does NOT execute automatically — it waits until the caller
 * invokes {@link load}. This supports the "click to view" lazy-loading
 * pattern: the user explicitly expands the viewer, and only then does
 * the backend get called to resolve the content location.
 *
 * For PDF/IMAGE documents the response contains a URL (presigned S3 URL
 * in production, backend-relative URL in development). For TEXT documents
 * the response contains the inline text content.
 */
export function useDocumentContent(documentId: string) {
  const { data, isLoading, isError, error, refetch, isFetched } =
    useQuery<DocumentContentLocationResponse>({
      queryKey: ['document-content', documentId],
      queryFn: () => fetchDocumentContentLocation(documentId),
      enabled: false, // Lazy — only runs when refetch() is called
    })

  const load = useCallback(() => {
    refetch()
  }, [refetch])

  return {
    content: data ?? null,
    isLoading,
    isError,
    error,
    isFetched,
    load,
  }
}
