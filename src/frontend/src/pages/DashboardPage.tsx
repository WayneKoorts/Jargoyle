import type { UserProfile } from '../api/auth'
import DocumentList from '../components/DocumentList'
import Layout from '../components/Layout'

interface DashboardPageProps {
  user: UserProfile
  onLogout: () => void
}

export default function DashboardPage({ user, onLogout }: DashboardPageProps) {
  return (
    <Layout user={user} onLogout={onLogout}>
      <div className="mx-auto max-w-4xl px-6 py-8">
        <h2 className="text-2xl font-bold text-slate-900">Your Documents</h2>
        <div className="mt-6">
          <DocumentList />
        </div>
      </div>
    </Layout>
  )
}
