import { Link } from 'react-router-dom'
import type { AdminUser } from '../api/admin'
import { displayUserName, formatDate } from '../utils/display'

const SORT_OPTIONS = [
  { value: 'displayName', label: 'Display name' },
  { value: 'email', label: 'Email' },
  { value: 'role', label: 'Role' },
  { value: 'createdAt', label: 'Date joined' },
  { value: 'lastLoginAt', label: 'Last login' },
] as const

const ROLE_COLOURS: Record<string, string> = {
  USER: 'bg-slate-100 text-slate-700',
  ADMIN: 'bg-amber-100 text-amber-800',
}

const STATUS_COLOURS = {
  enabled: 'bg-emerald-100 text-emerald-800',
  disabled: 'bg-rose-100 text-rose-800',
} as const

function roleClasses(role: string): string {
  return ROLE_COLOURS[role] ?? 'bg-slate-100 text-slate-700'
}

interface UserListProps {
  users: AdminUser[]
  totalElements: number
  totalPages: number
  isFirst: boolean
  isLast: boolean
  isEmpty: boolean
  isLoading: boolean
  isError: boolean
  page: number
  onPageChange: (page: number) => void
  sortField: string
  onSortFieldChange: (field: string) => void
  sortDirection: 'asc' | 'desc'
  onSortDirectionToggle: () => void
  linkPrefix?: string
}

export default function UserList({
  users,
  totalElements,
  totalPages,
  isFirst,
  isLast,
  isEmpty,
  isLoading,
  isError,
  page,
  onPageChange,
  sortField,
  onSortFieldChange,
  sortDirection,
  onSortDirectionToggle,
  linkPrefix = '/admin/users',
}: UserListProps) {
  if (isLoading && isEmpty) {
    return <div className="py-12 text-center text-slate-400">Loading…</div>
  }

  if (isError) {
    return (
      <div className="py-12 text-center">
        <p className="text-slate-500">Something went wrong loading users.</p>
        <button
          onClick={() => onPageChange(0)}
          className="mt-3 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
        >
          Retry
        </button>
      </div>
    )
  }

  if (isEmpty && !isLoading) {
    return (
      <div className="py-12 text-center text-slate-500">
        No users found.
      </div>
    )
  }

  return (
    <div>
      {/* Sort controls */}
      <div className="mb-4 flex items-center justify-between">
        <span className="text-sm text-slate-500">
          {totalElements} {totalElements === 1 ? 'user' : 'users'}
        </span>
        <div className="flex items-center gap-2">
          <label htmlFor="user-sort-field" className="text-sm text-slate-500">
            Sort by
          </label>
          <select
            id="user-sort-field"
            value={sortField}
            onChange={(e) => onSortFieldChange(e.target.value)}
            className="rounded-md border border-slate-300 pl-2 py-1.5 text-sm text-slate-700"
          >
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button
            onClick={onSortDirectionToggle}
            title={sortDirection === 'asc' ? 'Ascending' : 'Descending'}
            className="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm text-slate-700 shadow-sm transition-colors hover:border-slate-400 hover:bg-slate-50 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          >
            {sortDirection === 'asc' ? '↑' : '↓'}
          </button>
        </div>
      </div>

      {/* User cards */}
      <ul className="space-y-3">
        {users.map((user) => (
          <li
            key={user.id}
            className="rounded-lg border border-slate-200 bg-white p-4 transition-shadow hover:shadow-md"
          >
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <Link
                  to={`${linkPrefix}/${user.id}`}
                  className="text-base font-medium text-slate-900 hover:text-slate-600"
                >
                  {displayUserName(user)}
                </Link>
                <p className="mt-0.5 text-sm text-slate-500">{user.email}</p>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${roleClasses(user.role)}`}>
                    {user.role}
                  </span>
                  <span
                    className={`rounded px-2 py-0.5 text-xs font-medium ${
                      user.enabled ? STATUS_COLOURS.enabled : STATUS_COLOURS.disabled
                    }`}
                  >
                    {user.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                  <span className="text-xs text-slate-400">
                    Joined {formatDate(user.createdAt)}
                  </span>
                  <span className="text-xs text-slate-400">
                    {user.lastLoginAt ? `Last login ${formatDate(user.lastLoginAt)}` : 'Never logged in'}
                  </span>
                </div>
              </div>
            </div>
          </li>
        ))}
      </ul>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-between">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={isFirst}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-slate-500">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={isLast}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
