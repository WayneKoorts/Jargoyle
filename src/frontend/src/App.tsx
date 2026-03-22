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
  const { user, isLoading, isAuthenticated, isAdmin, isEnabled, logout } = useAuth()

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

  if (!isEnabled) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6">
        <div className="w-full max-w-lg rounded-2xl border border-amber-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 text-2xl">
            ⏳
          </div>
          <h1 className="mt-4 text-2xl font-bold text-slate-900">Account awaiting approval</h1>
          <p className="mt-3 text-sm leading-6 text-slate-600">
            Your account has been created, but an admin still needs to enable it before you can use Jargoyle.
          </p>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            If you think this is a mistake, please contact the team that invited you.
          </p>
          <button
            onClick={() => void logout()}
            className="mt-6 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
          >
            Log out
          </button>
        </div>
      </main>
    )
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
