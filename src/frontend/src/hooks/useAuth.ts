import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCurrentUser, logout as logoutApi } from '../api/auth'

export function useAuth() {
  const queryClient = useQueryClient()

  const { data: user, isLoading, isError } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: fetchCurrentUser,
    // 401 is expected for logged-out users — don't retry it.
    retry: false,
  })

  const isAuthenticated = !!user && !isError

  async function logout() {
    // Clear the cached user immediately so the UI switches to the login page
    // before the network request completes.
    queryClient.setQueryData(['auth', 'me'], null)
    await logoutApi()
    queryClient.removeQueries({ queryKey: ['auth', 'me'] })
  }

  return { user, isLoading, isAuthenticated, logout }
}
