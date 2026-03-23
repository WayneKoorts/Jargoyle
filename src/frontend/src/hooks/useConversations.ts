import { useQuery } from '@tanstack/react-query'
import { fetchConversations } from '../api/conversations'

/**
 * Fetches all conversations for a document, ordered newest-to-oldest
 * by creation time.
 *
 * The query key includes the documentId so each document gets its own cached
 * conversation list — navigating between documents won't show stale data.
 */
export function useConversations(documentId: string) {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['conversations', documentId],
    queryFn: () => fetchConversations(documentId),
    // Skip the query when no documentId is provided — e.g. when the
    // document page is still loading or the document isn't READY yet.
    enabled: documentId.length > 0,
  })

  return {
    conversations: data ?? [],
    isLoading,
    isError,
    refetch,
  }
}
