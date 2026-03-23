import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import type { UserProfile } from '../../api/auth'
import AdminUserDetailsPage from './AdminUserDetailsPage'
import { server } from '../../test/msw-server'
import { renderWithProviders } from '../../test/test-utils'

const currentUser: UserProfile = {
  id: 'user-1',
  email: 'admin@example.com',
  displayName: 'Admin User',
  oauthProvider: 'google',
  role: 'ADMIN',
  enabled: true,
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route
        path="/admin/users/:id"
        element={<AdminUserDetailsPage user={currentUser} onLogout={vi.fn()} />}
      />
    </Routes>,
    {
      routerProps: {
        initialEntries: ['/admin/users/user-2'],
      },
    },
  )
}

describe('AdminUserDetailsPage', () => {
  it('lets admins enable a disabled user', async () => {
    let capturedBody: Record<string, string | boolean> | undefined

    server.use(
      http.put('/api/admin/users/:id', async ({ params, request }) => {
        capturedBody = await request.json() as Record<string, string | boolean>

        return HttpResponse.json({
          id: params.id,
          email: 'regular@example.com',
          displayName: 'Regular User',
          oauthProvider: 'google',
          role: 'USER',
          enabled: true,
          createdAt: '2026-02-20T14:00:00Z',
          lastLoginAt: null,
        })
      }),
    )

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'Regular User' })).toBeInTheDocument()
    })

    const checkbox = screen.getByRole('checkbox')
    expect(checkbox).not.toBeChecked()
    expect(screen.getByText('Disabled')).toBeInTheDocument()

    await userEvent.click(checkbox)
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => {
      expect(screen.getByText('User updated successfully.')).toBeInTheDocument()
    })

    expect(capturedBody).toMatchObject({
      role: 'USER',
      enabled: true,
    })
  })
})
