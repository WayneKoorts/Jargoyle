import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/test-utils'
import UserList from './UserList'
import type { AdminUser } from '../api/admin'

/** Helper to build a minimal AdminUser with overrides */
function makeUser(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 'user-1',
    email: 'test@example.com',
    displayName: 'Test User',
    oauthProvider: 'google',
    role: 'USER',
    enabled: true,
    createdAt: '2026-03-01T12:00:00Z',
    lastLoginAt: null,
    ...overrides,
  }
}

const noopFn = () => {}

/** Default props for a loaded, single-page list */
function defaultProps(overrides: Partial<React.ComponentProps<typeof UserList>> = {}) {
  return {
    users: [makeUser()],
    totalElements: 1,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    isEmpty: false,
    isLoading: false,
    isError: false,
    page: 0,
    onPageChange: noopFn,
    sortField: 'displayName',
    onSortFieldChange: noopFn,
    sortDirection: 'asc' as const,
    onSortDirectionToggle: noopFn,
    ...overrides,
  }
}

describe('UserList', () => {
  it('shows loading state', () => {
    renderWithProviders(
      <UserList {...defaultProps({ isLoading: true, isEmpty: true, users: [] })} />,
    )

    expect(screen.getByText('Loading…')).toBeInTheDocument()
  })

  it('shows error state with retry button', async () => {
    const onPageChange = vi.fn()

    renderWithProviders(
      <UserList {...defaultProps({ isError: true, onPageChange })} />,
    )

    expect(screen.getByText('Something went wrong loading users.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('shows empty state when no users', () => {
    renderWithProviders(
      <UserList {...defaultProps({ isEmpty: true, users: [] })} />,
    )

    expect(screen.getByText('No users found.')).toBeInTheDocument()
  })

  it('renders user cards with name, email, role badge, and enabled status', () => {
    const users = [
      makeUser({ id: 'u1', displayName: 'Alice', email: 'alice@example.com', role: 'ADMIN', enabled: true }),
      makeUser({ id: 'u2', displayName: 'Bob', email: 'bob@example.com', role: 'USER', enabled: false }),
    ]

    renderWithProviders(
      <UserList {...defaultProps({ users, totalElements: 2 })} />,
    )

    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.getByText('Enabled')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.getByText('bob@example.com')).toBeInTheDocument()
    expect(screen.getByText('Disabled')).toBeInTheDocument()
    expect(screen.getByText('2 users')).toBeInTheDocument()
  })

  it('falls back to email when display name is empty', () => {
    const users = [makeUser({ displayName: '', email: 'fallback@example.com' })]

    renderWithProviders(
      <UserList {...defaultProps({ users })} />,
    )

    // The link text should be the email, not empty
    expect(screen.getByRole('link', { name: 'fallback@example.com' })).toBeInTheDocument()
  })

  it('shows "Never logged in" when lastLoginAt is null', () => {
    renderWithProviders(
      <UserList {...defaultProps()} />,
    )

    expect(screen.getByText('Never logged in')).toBeInTheDocument()
  })

  it('shows last login date when available', () => {
    const users = [makeUser({ lastLoginAt: '2026-03-18T09:00:00Z' })]

    renderWithProviders(
      <UserList {...defaultProps({ users })} />,
    )

    // Match the full "Last login <date>" text to avoid hitting the sort dropdown option
    expect(screen.getByText(/^Last login \d/)).toBeInTheDocument()
  })

  it('uses singular "user" for count of 1', () => {
    renderWithProviders(
      <UserList {...defaultProps({ totalElements: 1 })} />,
    )

    expect(screen.getByText('1 user')).toBeInTheDocument()
  })

  it('calls onSortFieldChange when sort dropdown changes', async () => {
    const onSortFieldChange = vi.fn()

    renderWithProviders(
      <UserList {...defaultProps({ onSortFieldChange })} />,
    )

    const select = screen.getByLabelText('Sort by')
    await userEvent.selectOptions(select, 'email')

    expect(onSortFieldChange).toHaveBeenCalledWith('email')
  })

  it('calls onSortDirectionToggle when direction button is clicked', async () => {
    const onSortDirectionToggle = vi.fn()

    renderWithProviders(
      <UserList {...defaultProps({ onSortDirectionToggle })} />,
    )

    await userEvent.click(screen.getByTitle('Ascending'))
    expect(onSortDirectionToggle).toHaveBeenCalledTimes(1)
  })

  it('shows pagination when there are multiple pages', () => {
    renderWithProviders(
      <UserList {...defaultProps({ totalPages: 3, isFirst: true, isLast: false })} />,
    )

    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled()
  })

  it('does not show pagination for a single page', () => {
    renderWithProviders(
      <UserList {...defaultProps({ totalPages: 1 })} />,
    )

    expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument()
  })

  it('calls onPageChange when pagination buttons are clicked', async () => {
    const onPageChange = vi.fn()

    renderWithProviders(
      <UserList {...defaultProps({ totalPages: 3, isFirst: false, isLast: false, page: 1, onPageChange })} />,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledWith(2)

    await userEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('links to detail page with correct prefix', () => {
    renderWithProviders(
      <UserList {...defaultProps({ linkPrefix: '/custom/path' })} />,
    )

    const link = screen.getByRole('link', { name: 'Test User' })
    expect(link).toHaveAttribute('href', '/custom/path/user-1')
  })
})
