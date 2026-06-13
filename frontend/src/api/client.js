import axios from 'axios'

const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokens({ accessToken, refreshToken }) {
  if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

// Callback invoked when refresh fails and the session is no longer valid.
let onAuthFailure = null
export function setOnAuthFailure(cb) {
  onAuthFailure = cb
}

const client = axios.create({
  baseURL: '/api',
})

// A bare axios instance (no interceptors) used to perform the refresh call so
// it can't recurse through the response interceptor.
const refreshClient = axios.create({ baseURL: '/api' })

client.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Single-flight refresh: queue requests that hit 401 while a refresh is in
// progress, then replay them once the new token is available.
let isRefreshing = false
let pendingQueue = []

function flushQueue(error, token) {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token)
  })
  pendingQueue = []
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { response, config } = error
    const originalRequest = config

    // Don't try to refresh for non-401s, missing config, the refresh call
    // itself, or requests we've already retried once.
    if (
      !response ||
      response.status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login')
    ) {
      return Promise.reject(error)
    }

    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      clearTokens()
      onAuthFailure?.()
      return Promise.reject(error)
    }

    if (isRefreshing) {
      // Wait for the in-flight refresh, then replay.
      return new Promise((resolve, reject) => {
        pendingQueue.push({ resolve, reject })
      })
        .then((token) => {
          originalRequest._retry = true
          originalRequest.headers.Authorization = `Bearer ${token}`
          return client(originalRequest)
        })
        .catch((err) => Promise.reject(err))
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const { data } = await refreshClient.post('/auth/refresh', { refreshToken })
      setTokens(data)
      flushQueue(null, data.accessToken)
      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
      return client(originalRequest)
    } catch (refreshError) {
      flushQueue(refreshError, null)
      clearTokens()
      onAuthFailure?.()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)

export default client
