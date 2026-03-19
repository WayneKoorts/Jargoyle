import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteDocument } from '../api/documents'

/**
 * React Query mutation for deleting a document.
 *
 * On success, invalidates queries starting with ['documents'] so the
 * document list refreshes when the user navigates back to the dashboard.
 */
export function useDeleteDocument() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => deleteDocument(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })
}
