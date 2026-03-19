import { apiClient } from './client'
import type { Page } from './types'

// Mirrors the backend AdminUserDto record
export interface AdminUser {
  id: string
  email: string
  displayName: string
  oauthProvider: string
  role: string
  createdAt: string
  lastLoginAt: string | null
}

export interface AdminUserListParams {
  page: number
  size: number
  sortField: string
  sortDirection: 'asc' | 'desc'
}

export interface UpdateUserRequest {
  role: string
  displayName?: string
  email?: string
}

export function fetchAdminUsers(params: AdminUserListParams): Promise<Page<AdminUser>> {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
    sort: `${params.sortField},${params.sortDirection}`,
  })

  return apiClient<Page<AdminUser>>(`/admin/users?${query}`)
}

export function fetchAdminUser(id: string): Promise<AdminUser> {
  return apiClient<AdminUser>(`/admin/users/${id}`)
}

export function updateAdminUser(id: string, data: UpdateUserRequest): Promise<AdminUser> {
  return apiClient<AdminUser>(`/admin/users/${id}`, {
    method: 'PUT',
    body: data,
  })
}

export function deleteAdminUser(id: string): Promise<void> {
  return apiClient<void>(`/admin/users/${id}`, { method: 'DELETE' })
}
