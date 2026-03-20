import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createDocumentUploadSession,
  finaliseDocumentUpload,
  uploadDocumentContent,
  type DocumentResponse,
} from '../api/documents'

/**
 * React Query mutation for uploading documents.
 *
 * On success, invalidates any query whose key starts with ['documents']
 * so the document list refreshes automatically — this covers every page
 * and sort combination without needing to know the exact query key shape.
 */
export function useUploadDocument() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (params: {
      file?: File
      text?: string
      fileName?: string
      onDocumentCreated?: (documentId: string) => void
    }) => {
      if (params.file) {
        const session = await createDocumentUploadSession({
          inputType: 'PDF',
          fileName: params.fileName ?? params.file.name,
        })
        params.onDocumentCreated?.(session.document.id)

        if (!session.uploadTarget) {
          throw new Error('Upload target missing from server response.')
        }

        await uploadDocumentContent(session.uploadTarget, params.file)
        return finaliseDocumentUpload(session.document.id)
      }

      const session = await createDocumentUploadSession({
        inputType: 'TEXT',
        text: params.text!,
      })
      params.onDocumentCreated?.(session.document.id)
      return session.document
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })
}

export type { DocumentResponse }
