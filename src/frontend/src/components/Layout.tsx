import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { UserProfile } from '../api/auth'
import AccountDropdown from './AccountDropdown'
import UploadDialog from './UploadDialog'

interface LayoutProps {
  user: UserProfile
  onLogout: () => void
  children: React.ReactNode
  /** Admin variant: shows badge, links home, hides upload button */
  variant?: 'default' | 'admin'
  /**
   * When true, makes the layout fill the viewport height with flexbox
   * so children can use flex-1 to consume remaining space. Used by
   * the document detail page's split layout.
   */
  fullHeight?: boolean
}

/**
 * Shared page wrapper providing consistent nav bar across all pages.
 *
 * The upload button and dialog state live here so they're available
 * on every non-admin page without prop-drilling or context.
 */
export default function Layout({ user, onLogout, children, variant = 'default', fullHeight = false }: LayoutProps) {
  const [isUploadOpen, setIsUploadOpen] = useState(false)
  const isAdmin = variant === 'admin'

  // When fullHeight is active, prevent html/body from creating a second
  // scrollbar. The viewport-locked layout handles all scrolling internally.
  useEffect(() => {
    if (!fullHeight) return
    document.documentElement.style.overflow = 'hidden'
    document.body.style.overflow = 'hidden'
    return () => {
      document.documentElement.style.overflow = ''
      document.body.style.overflow = ''
    }
  }, [fullHeight])

  return (
    <main className={`bg-slate-50 ${fullHeight ? 'flex h-screen flex-col overflow-hidden' : 'min-h-screen'}`}>
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
          <AccountDropdown user={user} onLogout={onLogout} />
        </div>
      </header>

      {fullHeight ? (
        <div className="flex flex-1 flex-col overflow-hidden">{children}</div>
      ) : (
        children
      )}

      {!isAdmin && (
        <UploadDialog open={isUploadOpen} onClose={() => setIsUploadOpen(false)} />
      )}
    </main>
  )
}
