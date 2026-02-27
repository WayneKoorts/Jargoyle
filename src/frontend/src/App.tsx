import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

/**
 * Inner component that uses useAuth — must be rendered inside QueryClientProvider
 * and BrowserRouter so it has access to both contexts.
 */
function AppRoutes() {
  const { user, isLoading, isAuthenticated, logout } = useAuth()

  if (isLoading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="text-slate-400">Loading…</div>
      </main>
    )
  }

  if (!isAuthenticated || !user) {
    return <LoginPage />
  }

  return (
    <Routes>
      <Route path="/" element={<DashboardPage user={user} onLogout={logout} />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
