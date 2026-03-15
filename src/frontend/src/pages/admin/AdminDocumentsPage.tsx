import { Link } from 'react-router-dom'
import type { UserProfile } from '../../api/auth'

interface AdminDocumentsPageProps {
  user: UserProfile
  onLogout: () => void
}

export default function AdminDocumentsPage({ user, onLogout }: AdminDocumentsPageProps) {
  return (
    <main className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
        <div className="flex items-center gap-4">
          <Link to="/" className="text-lg font-semibold text-slate-900 hover:text-slate-700">
            Jargoyle
          </Link>
          <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
            Admin
          </span>
        </div>
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

      <div className="mx-auto max-w-4xl px-6 py-12">
        <div className="flex items-center gap-3">
          <Link to="/admin" className="text-sm text-slate-500 hover:text-slate-700">
            Admin
          </Link>
          <span className="text-slate-300">/</span>
          <h2 className="text-2xl font-bold text-slate-900">All Documents</h2>
        </div>
        <p className="mt-4 text-slate-500">
          Document management is coming soon.
        </p>
      </div>
    </main>
  )
}
