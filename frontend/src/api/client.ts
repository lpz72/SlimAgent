import axios from 'axios'

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

export const http = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

export function unwrapApiResponse<T>(response: { data: { code: number; message: string; data: T } }): T {
  const body = response.data
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}
