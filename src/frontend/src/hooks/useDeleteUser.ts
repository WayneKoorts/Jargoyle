import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteAdminUser } from '../api/admin'

/**
 * React Query mutation for deleting a user (admin only).
 *
 * On success, invalidates the admin user list so it refreshes
 * when the admin navigates back to the users page.
 */
export function useDeleteUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => deleteAdminUser(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}
