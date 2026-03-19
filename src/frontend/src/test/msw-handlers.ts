import { http, HttpResponse } from 'msw'

/**
 * Default MSW handlers shared across all tests.
 * Individual tests can override these with server.use(...) for
 * specific scenarios (e.g. error responses, empty lists).
 */
export const handlers = [
  // Auth — default to an authenticated regular user
  http.get('/api/auth/me', () => {
    return HttpResponse.json({
      id: 'user-1',
      email: 'test@example.com',
      displayName: 'Test User',
      oauthProvider: 'google',
      role: 'USER',
    })
  }),

  // Documents list — default to a single-page result
  http.get('/api/documents', () => {
    return HttpResponse.json({
      content: [
        {
          id: 'doc-1',
          title: 'Test Document',
          documentType: 'BILL',
          inputType: 'PDF',
          status: 'READY',
          originalFilename: 'test.pdf',
          textPreview: null,
          createdAt: '2026-03-01T12:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      numberOfElements: 1,
      first: true,
      last: true,
      empty: false,
    })
  }),

  // Document detail
  http.get('/api/documents/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      title: 'Test Document',
      documentType: 'BILL',
      inputType: 'PDF',
      originalFilename: 'test.pdf',
      status: 'READY',
      errorMessage: null,
      summary: null,
      createdAt: '2026-03-01T12:00:00Z',
    })
  }),

  // Document delete
  http.delete('/api/documents/:id', () => {
    return new HttpResponse(null, { status: 204 })
  }),

  // Document upload (file or text)
  http.post('/api/documents', () => {
    return HttpResponse.json({
      id: 'doc-new',
      title: null,
      documentType: 'OTHER',
      inputType: 'PDF',
      originalFilename: 'uploaded.pdf',
      status: 'UPLOADING',
      errorMessage: null,
      summary: null,
      createdAt: '2026-03-19T12:00:00Z',
    })
  }),

  // Logout
  http.post('/api/auth/logout', () => {
    return new HttpResponse(null, { status: 204 })
  }),
]
