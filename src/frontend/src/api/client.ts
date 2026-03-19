import { API_BASE_URL } from '../constants'

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

/**
 * Thin fetch wrapper for backend API calls.
 * - Prepends /api base path
 * - Sends session cookies (credentials: 'include')
 * - Sets JSON content type by default
 * - Throws on non-OK responses
 */
/**
 * Sends a FormData request (multipart/form-data) to the backend.
 * Does NOT set Content-Type — the browser adds it automatically with
 * the correct multipart boundary string.
 */
export async function apiFormData<T>(
  path: string,
  formData: FormData,
  options: Omit<RequestInit, 'body'> = {},
): Promise<T> {
  const { headers, ...rest } = options

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
    headers: { ...headers },
    ...rest,
  })

  if (!response.ok) {
    const errorBody = await response.text().catch(() => '')
    throw new Error(errorBody || `API error: ${response.status} ${response.statusText}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export async function apiClient<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { body, headers, ...rest } = options

  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
    ...rest,
  })

  if (!response.ok) {
    throw new Error(`API error: ${response.status} ${response.statusText}`)
  }

  // 204 No Content — nothing to parse
  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}
