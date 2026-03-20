import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useUploadDocument } from './useUploadDocument'
import type { ReactNode } from 'react'

function createWrapper() {
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }

  return { Wrapper, queryClient }
}

describe('useUploadDocument', () => {
  it('calls file upload when file param is provided', async () => {
    let createdSession = false
    let receivedFormData = false
    let finalised = false

    server.use(
      http.post('/api/documents/uploads', async ({ request }) => {
        const body = await request.json() as Record<string, string>
        createdSession = body.inputType === 'PDF'
        return HttpResponse.json({
          document: {
            id: 'doc-new',
            title: null,
            documentType: 'OTHER',
            inputType: 'PDF',
            originalFilename: 'test.pdf',
            status: 'PENDING_UPLOAD',
            errorMessage: null,
            summary: null,
            createdAt: '2026-03-19T12:00:00Z',
          },
          uploadTarget: {
            url: '/documents/doc-new/content',
            method: 'PUT',
          },
        })
      }),
      http.put('/api/documents/doc-new/content', async ({ request }) => {
        const body = await request.formData()
        receivedFormData = body.has('file')
        return HttpResponse.json({
          id: 'doc-new',
          title: null,
          documentType: 'OTHER',
          inputType: 'PDF',
          originalFilename: 'test.pdf',
          status: 'UPLOADING',
          errorMessage: null,
          summary: null,
          createdAt: '2026-03-19T12:00:00Z',
        })
      }),
      http.post('/api/documents/doc-new/finalise', () => {
        finalised = true
        return HttpResponse.json({
          id: 'doc-new',
          title: null,
          documentType: 'OTHER',
          inputType: 'PDF',
          originalFilename: 'test.pdf',
          status: 'QUEUED',
          errorMessage: null,
          summary: null,
          createdAt: '2026-03-19T12:00:00Z',
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useUploadDocument(), { wrapper: Wrapper })

    const testFile = new File(['content'], 'test.pdf', { type: 'application/pdf' })
    result.current.mutate({ file: testFile })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(createdSession).toBe(true)
    expect(receivedFormData).toBe(true)
    expect(finalised).toBe(true)
  })

  it('calls text upload when text param is provided', async () => {
    let receivedText = false

    server.use(
      http.post('/api/documents/uploads', async ({ request }) => {
        const body = await request.json() as Record<string, string>
        receivedText = body.text === 'Some document text'
        return HttpResponse.json({
          document: {
            id: 'doc-new',
            title: null,
            documentType: 'OTHER',
            inputType: 'TEXT',
            originalFilename: null,
            status: 'QUEUED',
            errorMessage: null,
            summary: null,
            createdAt: '2026-03-19T12:00:00Z',
          },
          uploadTarget: null,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useUploadDocument(), { wrapper: Wrapper })

    result.current.mutate({ text: 'Some document text' })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(receivedText).toBe(true)
  })

  it('invalidates documents queries on success', async () => {
    const { Wrapper, queryClient } = createWrapper()

    queryClient.setQueryData(['documents', { page: 0 }], { content: [] })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUploadDocument(), { wrapper: Wrapper })

    const testFile = new File(['content'], 'test.pdf', { type: 'application/pdf' })
    result.current.mutate({ file: testFile })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['documents'] }),
    )
  })
})
