import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createConversation } from '../api/conversations'

/**
 * React Query mutation for creating a new conversation on a document.
 *
 * On success, invalidates the conversation list query so the
 * ConversationSidebar picks up the new entry without a manual refetch.
 *
 * Callers should use `mutateAsync` rather than `mutate` because
 * they typically need the result (conversation ID + suggested questions)
 * to update local state immediately after creation.
 */
export function useCreateConversation(documentId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => createConversation(documentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversations', documentId] })
    },
  })
}
