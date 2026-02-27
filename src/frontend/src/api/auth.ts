import { apiClient } from './client'

export interface UserProfile {
  id: string
  email: string
  displayName: string
  oauthProvider: string
}

export function fetchCurrentUser(): Promise<UserProfile> {
  return apiClient<UserProfile>('/auth/me')
}

export function logout(): Promise<void> {
  return apiClient('/auth/logout', { method: 'POST' })
}

export const GOOGLE_AUTH_URL = '/oauth2/authorization/google'
