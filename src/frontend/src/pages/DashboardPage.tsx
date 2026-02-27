import type { UserProfile } from '../api/auth'

interface DashboardPageProps {
  user: UserProfile
  onLogout: () => void
}

export default function DashboardPage({ user, onLogout }: DashboardPageProps) {
  return (
    <main className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
        <h1 className="text-lg font-semibold text-slate-900">Jargoyle</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-slate-600">{user.displayName}</span>
          <button
            onClick={onLogout}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
          >
            Sign out
          </button>
        </div>
      </header>

      <div className="mx-auto max-w-3xl px-6 py-12 text-center">
        <h2 className="text-2xl font-bold text-slate-900">
          Welcome, {user.displayName}
        </h2>
        <p className="mt-2 text-slate-500">
          Your documents will appear here once uploading is available.
        </p>
      </div>
    </main>
  )
}
