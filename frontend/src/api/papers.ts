import { http } from './http'
import type { ApiResponse, PaperSearchResult } from '../types'

export async function uploadPaper(file: File, onProgress?: (percent: number) => void) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ApiResponse<{ fileName: string; filePath: string; fileSize: number }>>('/upload', form, {
    timeout: 300_000,
    onUploadProgress: event => onProgress?.(event.total ? Math.round(event.loaded * 100 / event.total) : 0),
  })
  return data.data
}

export async function searchPapers(query: string, topK: number) {
  const { data } = await http.get<ApiResponse<PaperSearchResult[]>>('/papers/search', { params: { query, topK } })
  return data.data
}

export async function checkMilvus() {
  const { data } = await http.get<{ message: string; collections: string[] }>('/milvus/health', { baseURL: '' })
  return data
}
