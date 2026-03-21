import { useQuery } from '@tanstack/react-query'
import { fetchConversations } from '../api/conversations'

/**
 * Fetches all conversations for a document, ordered by most recent activity.
 *
 * The query key includes the documentId so each document gets its own cached
 * conversation list — navigating between documents won't show stale data.
 */
export function useConversations(documentId: string) {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['conversations', documentId],
    queryFn: () => fetchConversations(documentId),
  })

  return {
    conversations: data ?? [],
    isLoading,
    isError,
    refetch,
  }
}
