import { Link } from 'react-router-dom'
import type { UserProfile } from '../../api/auth'
import Layout from '../../components/Layout'

interface AdminDashboardPageProps {
  user: UserProfile
  onLogout: () => void
}

const adminLinks = [
  { to: '/admin/users', title: 'Users', description: 'View and manage user accounts.' },
  { to: '/admin/documents', title: 'Documents', description: 'View and manage documents across all users.' },
]

export default function AdminDashboardPage({ user, onLogout }: AdminDashboardPageProps) {
  return (
    <Layout user={user} onLogout={onLogout} variant="admin">
      <div className="mx-auto max-w-4xl px-6 py-12">
        <h2 className="text-2xl font-bold text-slate-900">Admin Dashboard</h2>
        <p className="mt-2 text-slate-500">
          Manage users and documents across the platform.
        </p>

        <div className="mt-8 grid gap-6 sm:grid-cols-2">
          {adminLinks.map((adminLink) => (
            <Link
              className="group rounded-lg border border-slate-200 bg-white p-6 transition-shadow hover:shadow-md"
              key={adminLink.to}
              to={adminLink.to}
            >
              <h3 className="text-lg font-semibold text-slate-900 group-hover:text-slate-700">
                {adminLink.title}
              </h3>
              <p className="mt-1 text-sm text-slate-500">
                {adminLink.description}
              </p>
            </Link>
          ))}
        </div>
      </div>
    </Layout>
  )
}
