import client, {
  clearTokens,
  getRefreshToken,
  setTokens,
} from './client'

export async function register(email, password) {
  const { data } = await client.post('/auth/register', { email, password })
  return data
}

export async function login(email, password) {
  const { data } = await client.post('/auth/login', { email, password })
  setTokens(data)
  return data
}

export async function logout() {
  const refreshToken = getRefreshToken()
  try {
    if (refreshToken) {
      await client.post('/auth/logout', { refreshToken })
    }
  } catch {
    // Ignore network/server errors on logout; we clear local state regardless.
  } finally {
    clearTokens()
  }
}
