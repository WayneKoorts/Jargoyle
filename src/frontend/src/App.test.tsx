import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import App from './App'
import { server } from './test/msw-server'

describe('App', () => {
  it('shows a clear waiting-for-approval message for disabled users', async () => {
    server.use(
      http.get('/api/auth/me', () => {
        return HttpResponse.json({
          id: 'user-2',
          email: 'pending@example.com',
          displayName: 'Pending User',
          oauthProvider: 'google',
          role: 'USER',
          enabled: false,
        })
      }),
    )

    render(<App />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Account awaiting approval' })).toBeInTheDocument()
    })

    expect(
      screen.getByText('Your account has been created, but an admin still needs to enable it before you can use Jargoyle.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })
})
