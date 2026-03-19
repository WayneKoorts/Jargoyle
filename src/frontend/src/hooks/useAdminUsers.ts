import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { fetchAdminUsers, type AdminUserListParams } from '../api/admin'

/**
 * Fetches a paginated, sorted list of all users (admin only).
 *
 * keepPreviousData prevents the list from flashing empty while the next
 * page loads — the old content stays visible until the new data arrives.
 */
export function useAdminUsers(params: AdminUserListParams) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => fetchAdminUsers(params),
    placeholderData: keepPreviousData,
  })

  return {
    users: data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalElements: data?.totalElements ?? 0,
    isEmpty: data?.empty ?? true,
    isFirst: data?.first ?? true,
    isLast: data?.last ?? true,
    isLoading,
    isError,
  }
}
