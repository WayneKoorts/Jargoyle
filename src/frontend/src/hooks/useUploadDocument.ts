import { useMutation, useQueryClient } from '@tanstack/react-query'
import { uploadFile, uploadText, type DocumentResponse } from '../api/documents'

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
    mutationFn: (params: { file?: File; text?: string; fileName?: string }) => {
      if (params.file) {
        return uploadFile(params.file, params.fileName)
      }
      return uploadText(params.text!)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })
}

export type { DocumentResponse }
