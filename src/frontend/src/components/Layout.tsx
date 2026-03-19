import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { UserProfile } from '../api/auth'
import { displayUserName } from '../utils/display'
import UploadDialog from './UploadDialog'

interface LayoutProps {
  user: UserProfile
  onLogout: () => void
  children: React.ReactNode
  /** Admin variant: shows badge, links home, hides upload button */
  variant?: 'default' | 'admin'
}

/**
 * Shared page wrapper providing consistent nav bar across all pages.
 *
 * The upload button and dialog state live here so they're available
 * on every non-admin page without prop-drilling or context.
 */
export default function Layout({ user, onLogout, children, variant = 'default' }: LayoutProps) {
  const [isUploadOpen, setIsUploadOpen] = useState(false)
  const isAdmin = variant === 'admin'

  return (
    <main className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
        <div className="flex items-center gap-4">
          {isAdmin ? (
            <Link to="/" className="text-lg font-semibold text-slate-900 hover:text-slate-700">
              Jargoyle
            </Link>
          ) : (
            <h1 className="text-lg font-semibold text-slate-900">Jargoyle</h1>
          )}
          {isAdmin && (
            <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
              Admin
            </span>
          )}
        </div>
        <div className="flex items-center gap-4">
          {!isAdmin && (
            <button
              onClick={() => setIsUploadOpen(true)}
              className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-indigo-700"
            >
              Upload Document
            </button>
          )}
          {!isAdmin && user.role === 'ADMIN' && (
            <Link
              to="/admin"
              className="rounded-md bg-amber-100 px-3 py-1.5 text-sm font-medium text-amber-800 transition-colors hover:bg-amber-200"
            >
              Admin
            </Link>
          )}
          <span className="text-sm text-slate-600">{displayUserName(user)}</span>
          <button
            onClick={onLogout}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
          >
            Sign out
          </button>
        </div>
      </header>

      {children}

      {!isAdmin && (
        <UploadDialog open={isUploadOpen} onClose={() => setIsUploadOpen(false)} />
      )}
    </main>
  )
}
