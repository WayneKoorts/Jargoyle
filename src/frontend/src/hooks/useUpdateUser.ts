import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateAdminUser, type UpdateUserRequest } from '../api/admin'

/**
 * React Query mutation for updating an admin-managed user record.
 *
 * On success, invalidates both the user list and the individual user
 * cache so all views reflect the change immediately.
 */
export function useUpdateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateUserRequest }) =>
      updateAdminUser(id, data),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'user', variables.id] })
    },
  })
}
