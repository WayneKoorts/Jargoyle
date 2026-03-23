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
      enabled: true,
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
          enabled: true,
          createdAt: '2026-01-15T10:00:00Z',
          lastLoginAt: '2026-03-18T09:00:00Z',
        },
        {
          id: 'user-2',
          email: 'regular@example.com',
          displayName: 'Regular User',
          oauthProvider: 'google',
          role: 'USER',
          enabled: false,
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
      enabled: false,
      createdAt: '2026-02-20T14:00:00Z',
      lastLoginAt: null,
    })
  }),

  // Admin — update user
  http.put('/api/admin/users/:id', async ({ params, request }) => {
    const body = await request.json() as Record<string, string | boolean>
    return HttpResponse.json({
      id: params.id,
      email: body.email ?? 'regular@example.com',
      displayName: body.displayName ?? 'Regular User',
      oauthProvider: 'google',
      role: body.role ?? 'USER',
      enabled: body.enabled ?? false,
      createdAt: '2026-02-20T14:00:00Z',
      lastLoginAt: null,
    })
  }),

  // Admin — delete user
  http.delete('/api/admin/users/:id', () => {
    return new HttpResponse(null, { status: 204 })
  }),

  // Document original content location — default to TEXT
  http.get('/api/documents/:id/original', () => {
    return HttpResponse.json({
      url: null,
      text: 'Sample document text content.',
      inputType: 'TEXT',
    })
  }),

  // Document original content stream (local dev fallback)
  http.get('/api/documents/:id/original/stream', () => {
    return new HttpResponse('Sample document text content.', {
      headers: { 'Content-Type': 'text/plain; charset=UTF-8' },
    })
  }),

  // Conversations — create
  http.post('/api/documents/:documentId/conversations', ({ params }) => {
    return HttpResponse.json(
      {
        id: 'conv-1',
        documentId: params.documentId,
        suggestedQuestions: [
          { text: 'What is the main topic?', category: 'General' },
          { text: 'Are there any costs mentioned?', category: 'Costs' },
        ],
      },
      { status: 201 },
    )
  }),

  // Conversations — list for document
  http.get('/api/documents/:documentId/conversations', ({ params }) => {
    return HttpResponse.json([
      {
        id: 'conv-1',
        documentId: params.documentId,
        title: 'New conversation',
        messageCount: 2,
        createdAt: '2026-03-20T10:00:00Z',
        lastMessageAt: '2026-03-20T10:05:00Z',
      },
    ])
  }),

  // Messages — paginated list (newest-first)
  http.get('/api/conversations/:conversationId/messages', () => {
    return HttpResponse.json({
      content: [
        {
          id: 'msg-2',
          role: 'ASSISTANT',
          content: 'This document is a utility bill.',
          sourceChunks: [{ chunkId: 'chunk-1', chunkIndex: 0, preview: 'Your monthly...' }],
          createdAt: '2026-03-20T10:01:00Z',
        },
        {
          id: 'msg-1',
          role: 'USER',
          content: 'What is this document about?',
          sourceChunks: null,
          createdAt: '2026-03-20T10:00:00Z',
        },
      ],
      number: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
      numberOfElements: 2,
      first: true,
      last: true,
      empty: false,
    })
  }),

  // Chat — SSE stream response
  http.post('/api/conversations/:conversationId/messages', () => {
    const sseBody = [
      'data: {"type":"TOKEN","content":"This is ","messageId":null,"sourceChunks":null}\n\n',
      'data: {"type":"TOKEN","content":"a response.","messageId":null,"sourceChunks":null}\n\n',
      'data: {"type":"COMPLETE","content":null,"messageId":"msg-3","sourceChunks":[{"chunkId":"chunk-1","chunkIndex":0,"preview":"Your monthly..."}]}\n\n',
    ].join('')
    return new HttpResponse(sseBody, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    })
  }),
]
