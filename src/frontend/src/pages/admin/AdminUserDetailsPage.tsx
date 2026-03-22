import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { UserProfile } from '../../api/auth'
import Layout from '../../components/Layout'
import { useAdminUser } from '../../hooks/useAdminUser'
import { useUpdateUser } from '../../hooks/useUpdateUser'
import { useDeleteUser } from '../../hooks/useDeleteUser'
import { displayUserName, formatDate } from '../../utils/display'

interface AdminUserDetailsPageProps {
  user: UserProfile
  onLogout: () => void
}

const ROLE_COLOURS: Record<string, string> = {
  USER: 'bg-slate-100 text-slate-700',
  ADMIN: 'bg-amber-100 text-amber-800',
}

const STATUS_COLOURS = {
  enabled: 'bg-emerald-100 text-emerald-800',
  disabled: 'bg-rose-100 text-rose-800',
} as const

export default function AdminUserDetailsPage({ user: currentUser, onLogout }: AdminUserDetailsPageProps) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user: targetUser, isLoading, isError } = useAdminUser(id)
  const updateMutation = useUpdateUser()
  const deleteMutation = useDeleteUser()
  const deleteDialogRef = useRef<HTMLDialogElement>(null)

  // Kebab menu
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const handleClickOutside = useCallback((e: MouseEvent) => {
    if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
      setIsMenuOpen(false)
    }
  }, [])

  useEffect(() => {
    if (isMenuOpen) {
      window.document.addEventListener('mousedown', handleClickOutside)
      return () => window.document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isMenuOpen, handleClickOutside])

  // Local state for editable fields — null means "use server value"
  const [editedDisplayName, setEditedDisplayName] = useState<string | null>(null)
  const [editedEmail, setEditedEmail] = useState<string | null>(null)
  const [selectedRole, setSelectedRole] = useState<string | null>(null)
  const [selectedEnabled, setSelectedEnabled] = useState<boolean | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  // Effective values: local edits fall back to server data
  const effectiveDisplayName = editedDisplayName ?? targetUser?.displayName ?? ''
  const effectiveEmail = editedEmail ?? targetUser?.email ?? ''
  const effectiveRole = selectedRole ?? targetUser?.role ?? 'USER'
  const effectiveEnabled = selectedEnabled ?? targetUser?.enabled ?? false

  // Self-protection: disable role editing for your own account
  const isSelf = currentUser.id === id

  // Has anything changed from the server state?
  const hasChanges = targetUser != null && (
    effectiveDisplayName !== targetUser.displayName ||
    effectiveEmail !== targetUser.email ||
    effectiveRole !== targetUser.role ||
    effectiveEnabled !== targetUser.enabled
  )

  if (isLoading) {
    return (
      <Layout user={currentUser} onLogout={onLogout} variant="admin">
        <div className="mx-auto max-w-4xl px-6 py-8">
          <div className="py-12 text-center text-slate-400">Loading…</div>
        </div>
      </Layout>
    )
  }

  if (isError || !targetUser) {
    return (
      <Layout user={currentUser} onLogout={onLogout} variant="admin">
        <div className="mx-auto max-w-4xl px-6 py-8">
          <div className="py-12 text-center">
            <p className="text-slate-500">Could not load this user.</p>
            <Link
              to="/admin/users"
              className="mt-3 inline-block rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              Back to users
            </Link>
          </div>
        </div>
      </Layout>
    )
  }

  function handleSave() {
    if (!id) return
    setSaveSuccess(false)
    updateMutation.mutate(
      {
        id,
        data: {
          role: effectiveRole,
          displayName: effectiveDisplayName,
          email: effectiveEmail,
          enabled: effectiveEnabled,
        },
      },
      {
        onSuccess: () => {
          setSaveSuccess(true)
          // Reset local overrides — the query cache will have fresh data
          setEditedDisplayName(null)
          setEditedEmail(null)
          setSelectedRole(null)
          setSelectedEnabled(null)
        },
      },
    )
  }

  function clearEdits() {
    setEditedDisplayName(null)
    setEditedEmail(null)
    setSelectedRole(null)
    setSelectedEnabled(null)
    setSaveSuccess(false)
    updateMutation.reset()
  }

  return (
    <Layout user={currentUser} onLogout={onLogout} variant="admin">
      <div className="mx-auto max-w-4xl px-6 py-8">
        {/* Breadcrumb */}
        <div className="mb-6 flex items-center gap-2 text-sm">
          <Link to="/admin" className="text-slate-500 hover:text-slate-700">Admin</Link>
          <span className="text-slate-300">/</span>
          <Link to="/admin/users" className="text-slate-500 hover:text-slate-700">Users</Link>
          <span className="text-slate-300">/</span>
          <span className="text-slate-900 font-medium">{displayUserName(targetUser)}</span>
        </div>

        {/* User details card — editable fields + read-only metadata */}
        <div className="rounded-lg border border-slate-200 bg-white p-6">
          <div className="flex items-start justify-between gap-4">
            <h2 className="text-2xl font-bold text-slate-900">{displayUserName(targetUser)}</h2>
            <div className="flex items-center gap-2">
              <span className={`shrink-0 rounded px-2 py-0.5 text-xs font-medium ${ROLE_COLOURS[targetUser.role] ?? 'bg-slate-100 text-slate-700'}`}>
                {targetUser.role}
              </span>
              <span
                className={`shrink-0 rounded px-2 py-0.5 text-xs font-medium ${
                  effectiveEnabled ? STATUS_COLOURS.enabled : STATUS_COLOURS.disabled
                }`}
              >
                {effectiveEnabled ? 'Enabled' : 'Disabled'}
              </span>
              {/* Kebab menu */}
              <div ref={menuRef} className="relative">
                <button
                  onClick={() => setIsMenuOpen((prev) => !prev)}
                  className="rounded-md p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                  aria-label="More actions"
                >
                  <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M10 6a2 2 0 110-4 2 2 0 010 4zM10 12a2 2 0 110-4 2 2 0 010 4zM10 18a2 2 0 110-4 2 2 0 010 4z" />
                  </svg>
                </button>
                {isMenuOpen && (
                  <div className="absolute right-0 z-10 mt-1 w-40 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
                    <button
                      disabled={isSelf}
                      onClick={() => {
                        setIsMenuOpen(false)
                        deleteDialogRef.current?.showModal()
                      }}
                      className="flex w-full items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                      </svg>
                      Delete
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* Editable: Display Name */}
            <div>
              <label htmlFor="edit-display-name" className="text-xs font-medium uppercase tracking-wide text-slate-500">
                Display Name
              </label>
              <input
                id="edit-display-name"
                type="text"
                value={effectiveDisplayName}
                onChange={(e) => { setEditedDisplayName(e.target.value); setSaveSuccess(false) }}
                disabled={updateMutation.isPending}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-900 disabled:opacity-50"
              />
            </div>

            {/* Editable: Email */}
            <div>
              <label htmlFor="edit-email" className="text-xs font-medium uppercase tracking-wide text-slate-500">
                Email
              </label>
              <input
                id="edit-email"
                type="email"
                value={effectiveEmail}
                onChange={(e) => { setEditedEmail(e.target.value); setSaveSuccess(false) }}
                disabled={updateMutation.isPending}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-900 disabled:opacity-50"
              />
            </div>

            {/* Editable: Role */}
            <div>
              <label htmlFor="edit-role" className="text-xs font-medium uppercase tracking-wide text-slate-500">
                Role
              </label>
              {isSelf && (
                <p className="mt-1 text-xs text-amber-600">You cannot change your own role.</p>
              )}
              <select
                id="edit-role"
                value={effectiveRole}
                onChange={(e) => { setSelectedRole(e.target.value); setSaveSuccess(false) }}
                disabled={isSelf || updateMutation.isPending}
                className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700 disabled:opacity-50"
              >
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>

            <div>
              <label htmlFor="edit-enabled" className="text-xs font-medium uppercase tracking-wide text-slate-500">
                Access
              </label>
              <label
                htmlFor="edit-enabled"
                className="mt-1 flex cursor-pointer items-start gap-3 rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-700"
              >
                <input
                  id="edit-enabled"
                  type="checkbox"
                  checked={effectiveEnabled}
                  onChange={(e) => { setSelectedEnabled(e.target.checked); setSaveSuccess(false) }}
                  disabled={updateMutation.isPending}
                  className="mt-0.5 h-4 w-4 rounded border-slate-300 text-indigo-600"
                />
                <span>
                  <span className="block font-medium text-slate-900">
                    {effectiveEnabled ? 'User can access the application' : 'User is blocked until enabled'}
                  </span>
                  <span className="block text-xs text-slate-500">
                    New OAuth sign-ups start disabled until an admin enables them.
                  </span>
                </span>
              </label>
            </div>

            {/* Read-only: OAuth Provider */}
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">OAuth Provider</dt>
              <dd className="mt-1 text-sm text-slate-900">{targetUser.oauthProvider}</dd>
            </div>

            {/* Read-only: Date Joined */}
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Date Joined</dt>
              <dd className="mt-1 text-sm text-slate-900">{formatDate(targetUser.createdAt)}</dd>
            </div>

            {/* Read-only: Last Login */}
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Last Login</dt>
              <dd className="mt-1 text-sm text-slate-900">
                {targetUser.lastLoginAt ? formatDate(targetUser.lastLoginAt) : 'Never'}
              </dd>
            </div>
          </div>

          {/* Save / Cancel bar — only visible when something has changed */}
          {hasChanges && (
            <div className="mt-6 flex items-center gap-3 border-t border-slate-100 pt-4">
              <button
                onClick={handleSave}
                disabled={updateMutation.isPending}
                className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-indigo-700 disabled:opacity-50"
              >
                {updateMutation.isPending ? 'Saving…' : 'Save changes'}
              </button>
              <button
                onClick={clearEdits}
                disabled={updateMutation.isPending}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50"
              >
                Discard
              </button>
            </div>
          )}

          {saveSuccess && (
            <p className="mt-3 text-sm text-green-600">User updated successfully.</p>
          )}
          {updateMutation.error && (
            <p className="mt-3 text-sm text-red-600">{updateMutation.error.message}</p>
          )}
        </div>

      </div>

      {/* Delete confirmation dialog */}
      <dialog
        ref={deleteDialogRef}
        onClick={(e) => { if (e.target === deleteDialogRef.current) deleteDialogRef.current.close() }}
        className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-sm rounded-xl border-0 p-0 shadow-xl backdrop:bg-black/50"
      >
        <div className="p-6">
          <h3 className="text-lg font-semibold text-slate-900">Delete user</h3>
          <p className="mt-2 text-sm text-slate-600">
            Are you sure you want to delete <span className="font-medium text-slate-900">{displayUserName(targetUser)}</span>? This action cannot be undone.
          </p>
          {deleteMutation.error && (
            <p className="mt-3 text-sm text-red-600">{deleteMutation.error.message}</p>
          )}
          <div className="mt-6 flex justify-end gap-3">
            <button
              onClick={() => { deleteMutation.reset(); deleteDialogRef.current?.close() }}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (!id) return
                deleteMutation.mutate(id, {
                  onSuccess: () => {
                    deleteDialogRef.current?.close()
                    navigate('/admin/users')
                  },
                })
              }}
              className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-red-700 disabled:opacity-50"
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        </div>
      </dialog>
    </Layout>
  )
}
