import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 60_000,
})

http.interceptors.response.use(
  response => response,
  error => Promise.reject(new Error(error.response?.data?.message || error.response?.data || error.message || '请求失败')),
)
