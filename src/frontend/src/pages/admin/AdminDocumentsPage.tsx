import { Link } from 'react-router-dom'
import type { UserProfile } from '../../api/auth'
import Layout from '../../components/Layout'

interface AdminDocumentsPageProps {
  user: UserProfile
  onLogout: () => void
}

export default function AdminDocumentsPage({ user, onLogout }: AdminDocumentsPageProps) {
  return (
    <Layout user={user} onLogout={onLogout} variant="admin">
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
    </Layout>
  )
}
