const BASE_URL = '/api'

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
export async function apiClient<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { body, headers, ...rest } = options

  const response = await fetch(`${BASE_URL}${path}`, {
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
