import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { UserProfile } from '../../api/auth'
import Layout from '../../components/Layout'
import UserList from '../../components/UserList'
import { useAdminUsers } from '../../hooks/useAdminUsers'

const PAGE_SIZE = 20

interface AdminUsersPageProps {
  user: UserProfile
  onLogout: () => void
}

export default function AdminUsersPage({ user, onLogout }: AdminUsersPageProps) {
  const [page, setPage] = useState(0)
  const [sortField, setSortField] = useState('displayName')
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc')

  const result = useAdminUsers({ page, size: PAGE_SIZE, sortField, sortDirection })

  function handleSortFieldChange(field: string) {
    setSortField(field)
    setPage(0)
  }

  function toggleSortDirection() {
    setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
    setPage(0)
  }

  return (
    <Layout user={user} onLogout={onLogout} variant="admin">
      <div className="mx-auto max-w-4xl px-6 py-12">
        <div className="mb-6 flex items-center gap-3">
          <Link to="/admin" className="text-sm text-slate-500 hover:text-slate-700">
            Admin
          </Link>
          <span className="text-slate-300">/</span>
          <h2 className="text-2xl font-bold text-slate-900">All Users</h2>
        </div>
        <UserList
          users={result.users}
          totalElements={result.totalElements}
          totalPages={result.totalPages}
          isFirst={result.isFirst}
          isLast={result.isLast}
          isEmpty={result.isEmpty}
          isLoading={result.isLoading}
          isError={result.isError}
          page={page}
          onPageChange={setPage}
          sortField={sortField}
          onSortFieldChange={handleSortFieldChange}
          sortDirection={sortDirection}
          onSortDirectionToggle={toggleSortDirection}
        />
      </div>
    </Layout>
  )
}
