import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminUsersPage from './pages/admin/AdminUsersPage'
import AdminDocumentsPage from './pages/admin/AdminDocumentsPage'
import AdminUserDetailsPage from './pages/admin/AdminUserDetailsPage'
import DocumentDetailsPage from './pages/DocumentDetailsPage'

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
  const { user, isLoading, isAuthenticated, isAdmin, logout } = useAuth()

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
      <Route path="/documents/:id" element={<DocumentDetailsPage user={user} onLogout={logout} />} />
      {isAdmin && (
        <>
          <Route path="/admin" element={<AdminDashboardPage user={user} onLogout={logout} />} />
          <Route path="/admin/users" element={<AdminUsersPage user={user} onLogout={logout} />} />
          <Route path="/admin/users/:id" element={<AdminUserDetailsPage user={user} onLogout={logout} />} />
          <Route path="/admin/documents" element={<AdminDocumentsPage user={user} onLogout={logout} />} />
        </>
      )}
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
