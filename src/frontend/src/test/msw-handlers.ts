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

  http.post('/api/documents/uploads', async ({ request }) => {
    const body = await request.json() as Record<string, string | null>
    const inputType = body.inputType ?? 'PDF'

    return HttpResponse.json({
      document: {
        id: 'doc-new',
        title: null,
        documentType: 'OTHER',
        inputType,
        originalFilename: body.originalFilename ?? (inputType === 'PDF' ? 'uploaded.pdf' : null),
        status: inputType === 'TEXT' ? 'QUEUED' : 'PENDING_UPLOAD',
        errorMessage: null,
        summary: null,
        createdAt: '2026-03-19T12:00:00Z',
      },
      uploadTarget: inputType === 'PDF'
        ? { url: '/documents/doc-new/content', method: 'PUT' }
        : null,
    })
  }),

  http.put('/api/documents/:id/content', () => {
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

  http.post('/api/documents/:id/finalise', () => {
    return HttpResponse.json({
      id: 'doc-new',
      title: null,
      documentType: 'OTHER',
      inputType: 'PDF',
      originalFilename: 'uploaded.pdf',
      status: 'QUEUED',
      errorMessage: null,
      summary: null,
      createdAt: '2026-03-19T12:00:00Z',
    })
  }),

  // Logout
  http.post('/api/auth/logout', () => {
    return new HttpResponse(null, { status: 204 })
  }),

  // Admin — user list (default: two users, single page)
  http.get('/api/admin/users', () => {
    return HttpResponse.json({
      content: [
        {
          id: 'user-1',
          email: 'admin@example.com',
          displayName: 'Admin User',
          oauthProvider: 'google',
          role: 'ADMIN',
          createdAt: '2026-01-15T10:00:00Z',
          lastLoginAt: '2026-03-18T09:00:00Z',
        },
        {
          id: 'user-2',
          email: 'regular@example.com',
          displayName: 'Regular User',
          oauthProvider: 'google',
          role: 'USER',
          createdAt: '2026-02-20T14:00:00Z',
          lastLoginAt: null,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      numberOfElements: 2,
      first: true,
      last: true,
      empty: false,
    })
  }),

  // Admin — single user detail
  http.get('/api/admin/users/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      email: 'regular@example.com',
      displayName: 'Regular User',
      oauthProvider: 'google',
      role: 'USER',
      createdAt: '2026-02-20T14:00:00Z',
      lastLoginAt: null,
    })
  }),

  // Admin — update user
  http.put('/api/admin/users/:id', async ({ params, request }) => {
    const body = await request.json() as Record<string, string>
    return HttpResponse.json({
      id: params.id,
      email: body.email ?? 'regular@example.com',
      displayName: body.displayName ?? 'Regular User',
      oauthProvider: 'google',
      role: body.role ?? 'USER',
      createdAt: '2026-02-20T14:00:00Z',
      lastLoginAt: null,
    })
  }),

  // Admin — delete user
  http.delete('/api/admin/users/:id', () => {
    return new HttpResponse(null, { status: 204 })
  }),
]
