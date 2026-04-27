import { http, unwrapApiResponse } from './client'
import type { FileUploadResponse } from '@/types'

export async function uploadDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrapApiResponse<FileUploadResponse>(
    await http.post('/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }),
  )
}
