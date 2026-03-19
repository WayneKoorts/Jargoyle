import { useQuery } from '@tanstack/react-query'
import { fetchAdminUser, type AdminUser } from '../api/admin'

/**
 * React Query hook for fetching a single user by ID (admin only).
 *
 * enabled: !!id prevents the query from firing when the route param
 * is undefined (e.g. during initial render before React Router resolves).
 */
export function useAdminUser(id: string | undefined) {
  const { data, isLoading, isError, refetch } = useQuery<AdminUser>({
    queryKey: ['admin', 'user', id],
    queryFn: () => fetchAdminUser(id!),
    enabled: !!id,
  })

  return {
    user: data ?? null,
    isLoading,
    isError,
    refetch,
  }
}
