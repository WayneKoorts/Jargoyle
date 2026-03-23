import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/test-utils'
import Layout from './Layout'
import type { UserProfile } from '../api/auth'

// Mock HTMLDialogElement methods that jsdom doesn't implement.
// Layout renders UploadDialog which uses <dialog> showModal/close.
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn()
})

const regularUser: UserProfile = {
  id: 'user-1',
  email: 'test@example.com',
  displayName: 'Jane Smith',
  oauthProvider: 'google',
  role: 'USER',
  enabled: true,
}

const adminUser: UserProfile = {
  id: 'admin-1',
  email: 'admin@example.com',
  displayName: 'Admin Person',
  oauthProvider: 'google',
  role: 'ADMIN',
  enabled: true,
}

describe('Layout', () => {
  it('renders user display name', () => {
    renderWithProviders(
      <Layout user={regularUser} onLogout={vi.fn()}>
        <div>Content</div>
      </Layout>,
    )

    expect(screen.getByText('Jane Smith')).toBeInTheDocument()
  })

  it('shows Upload Document button in default variant', () => {
    renderWithProviders(
      <Layout user={regularUser} onLogout={vi.fn()}>
        <div>Content</div>
      </Layout>,
    )

    expect(screen.getByRole('button', { name: 'Upload Document' })).toBeInTheDocument()
  })

  it('hides Upload Document button in admin variant', () => {
    renderWithProviders(
      <Layout user={adminUser} onLogout={vi.fn()} variant="admin">
        <div>Content</div>
      </Layout>,
    )

    expect(screen.queryByRole('button', { name: 'Upload Document' })).not.toBeInTheDocument()
  })

  it('shows Admin link for admin users in default variant', () => {
    renderWithProviders(
      <Layout user={adminUser} onLogout={vi.fn()}>
        <div>Content</div>
      </Layout>,
    )

    expect(screen.getByRole('link', { name: 'Admin' })).toBeInTheDocument()
  })

  it('hides Admin link for non-admin users', () => {
    renderWithProviders(
      <Layout user={regularUser} onLogout={vi.fn()}>
        <div>Content</div>
      </Layout>,
    )

    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it('clicking Sign out calls onLogout', async () => {
    const onLogout = vi.fn()
    renderWithProviders(
      <Layout user={regularUser} onLogout={onLogout}>
        <div>Content</div>
      </Layout>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(onLogout).toHaveBeenCalledOnce()
  })
})
