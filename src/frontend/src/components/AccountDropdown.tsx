import { useEffect, useRef, useState } from 'react'
import { ArrowLeftRight, LogOut, User } from 'lucide-react'
import type { UserProfile } from '../api/auth'
import { GOOGLE_AUTH_SWITCH_ACCOUNT_URL } from '../constants'
import { displayUserName } from '../utils/display'

interface AccountDropdownProps {
  user: UserProfile
  onLogout: () => void
}

/**
 * Extracts up to two initials from a display name.
 * Falls back to "?" if the name is empty.
 */
function getInitials(name: string): string {
  const initials = name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map(part => part[0].toUpperCase())
    .join('')
  return initials || '?'
}

/**
 * Profile avatar with a dropdown menu for account-related actions.
 *
 * Consolidates user identity actions (account settings, switch account,
 * sign out) into a single nav bar control, replacing the previous
 * inline username + sign-out button.
 */
export default function AccountDropdown({ user, onLogout }: AccountDropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  // Close when clicking outside the dropdown
  useEffect(() => {
    if (!isOpen) return
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [isOpen])

  const displayName = displayUserName(user)
  const initials = getInitials(displayName)

  return (
    <div ref={dropdownRef} className="relative">
      {/* Avatar toggle — shows user initials in a coloured circle */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-600 text-xs font-semibold text-white transition-shadow hover:ring-2 hover:ring-indigo-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
        aria-label="Account menu"
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        {initials}
      </button>

      {/* Dropdown panel — kept in DOM for smooth exit animation */}
      <div
        className={`absolute right-0 z-50 mt-2 w-56 origin-top-right rounded-lg border border-slate-200 bg-white shadow-lg transition-all duration-150 ${
          isOpen
            ? 'scale-100 opacity-100'
            : 'pointer-events-none scale-95 opacity-0'
        }`}
        role="menu"
      >
        {/* User info header */}
        <div className="border-b border-slate-100 px-4 py-3">
          <p className="truncate text-sm font-medium text-slate-900">{displayName}</p>
          <p className="truncate text-xs text-slate-500">{user.email}</p>
        </div>

        <div className="py-1">
          {/* Account — placeholder for future account settings page */}
          <button
            onClick={() => setIsOpen(false)}
            className="flex w-full items-center gap-3 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-slate-50"
            role="menuitem"
          >
            <User className="h-4 w-4 text-slate-400" />
            Account
          </button>

          {/* Switch account — full-page navigation to Google OAuth with account chooser.
              The ?prompt=select_account query parameter signals our backend resolver to
              add prompt=select_account to the Google authorisation request. */}
          <a
            href={GOOGLE_AUTH_SWITCH_ACCOUNT_URL}
            className="flex items-center gap-3 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-slate-50"
            role="menuitem"
          >
            <ArrowLeftRight className="h-4 w-4 text-slate-400" />
            Switch account
          </a>
        </div>

        {/* Divider before destructive action */}
        <div className="border-t border-slate-100" />

        <div className="py-1">
          <button
            onClick={() => {
              setIsOpen(false)
              onLogout()
            }}
            className="flex w-full items-center gap-3 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-slate-50"
            role="menuitem"
          >
            <LogOut className="h-4 w-4 text-slate-400" />
            Sign out
          </button>
        </div>
      </div>
    </div>
  )
}
