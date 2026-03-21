import { useInfiniteQuery } from '@tanstack/react-query'
import { fetchMessages } from '../api/conversations'

/**
 * Fetches paginated messages for a conversation with infinite scrolling support.
 *
 * The backend returns messages newest-first (page 0 = most recent messages),
 * which is the natural pagination order for chat — "load more" fetches older
 * history. This hook reverses the data for chronological display so the
 * consuming component sees messages oldest-first (natural reading order).
 *
 * useInfiniteQuery (TanStack Query v5) manages the page cache automatically.
 * Each page is a Spring Data Page<Message> with metadata like `last` (boolean
 * indicating no more pages) and `number` (zero-based page index).
 */
export function useMessages(conversationId: string) {
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['messages', conversationId],
    queryFn: ({ pageParam }) => fetchMessages(conversationId, pageParam),
    // TanStack v5 requires an explicit initial page parameter
    initialPageParam: 0,
    // Spring's `last` field is true when there are no more pages.
    // Return the next page index, or undefined to signal "no more".
    getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
  })

  // The API returns pages newest-first, but we want chronological order
  // (oldest first) for display. Two reversals are needed:
  //   1. Reverse page order → oldest page first
  //   2. Reverse each page's content → oldest message first within each page
  //
  // Example: pages = [Page0:[msg-4,msg-3], Page1:[msg-2,msg-1]]  (newest-first)
  //   After: [msg-1, msg-2, msg-3, msg-4]  (chronological)
  const messages = data?.pages
    .slice()
    .reverse()
    .flatMap(page => [...page.content].reverse())
    ?? []

  return {
    messages,
    isLoading,
    loadMore: fetchNextPage,
    hasMore: hasNextPage ?? false,
    isLoadingMore: isFetchingNextPage,
  }
}
