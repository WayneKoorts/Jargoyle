import { apiClient, apiFormData } from './client'

describe('apiClient', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns parsed JSON on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: '1', name: 'test' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await apiClient<{ id: string; name: string }>('/test')
    expect(result).toEqual({ id: '1', name: 'test' })
  })

  it('returns undefined for 204 No Content', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 204 }),
    )

    const result = await apiClient('/test')
    expect(result).toBeUndefined()
  })

  it('throws on non-OK response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 403, statusText: 'Forbidden' }),
    )

    await expect(apiClient('/test')).rejects.toThrow('API error: 403 Forbidden')
  })

  it('sets JSON Content-Type and credentials', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({}), { status: 200 }),
    )

    await apiClient('/test')

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/test',
      expect.objectContaining({
        credentials: 'include',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
        }),
      }),
    )
  })

  it('serialises body to JSON', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({}), { status: 200 }),
    )

    await apiClient('/test', { method: 'POST', body: { key: 'value' } })

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/test',
      expect.objectContaining({
        body: JSON.stringify({ key: 'value' }),
      }),
    )
  })
})

describe('apiFormData', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns parsed JSON on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 'new' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const formData = new FormData()
    formData.append('file', new Blob(), 'test.pdf')

    const result = await apiFormData<{ id: string }>('/upload', formData)
    expect(result).toEqual({ id: 'new' })
  })

  it('does NOT set Content-Type header (browser sets multipart boundary)', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({}), { status: 200 }),
    )

    await apiFormData('/upload', new FormData())

    // The headers object should not contain Content-Type
    const callHeaders = fetchSpy.mock.calls[0][1]?.headers as Record<string, string>
    expect(callHeaders['Content-Type']).toBeUndefined()
  })

  it('throws with error body on non-OK response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('File too large', { status: 413 }),
    )

    await expect(apiFormData('/upload', new FormData())).rejects.toThrow('File too large')
  })

  it('falls back to status text when body read fails', async () => {
    const mockResponse = new Response(null, { status: 500, statusText: 'Internal Server Error' })
    // Override text() to simulate a read failure
    vi.spyOn(mockResponse, 'text').mockRejectedValue(new Error('read failed'))
    // Mark as non-OK
    Object.defineProperty(mockResponse, 'ok', { value: false })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(mockResponse)

    await expect(apiFormData('/upload', new FormData())).rejects.toThrow(
      'API error: 500 Internal Server Error',
    )
  })
})
