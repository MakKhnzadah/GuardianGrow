import { baseFetchJson, type FetchJsonOptions } from './base-fetch'
import { ApiError } from './problem-details'
import { useAuthStore } from '../features/auth/store'
import { API_BASE_URL } from './config'

function withBase(path: string) {
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  if (!path.startsWith('/')) return `${API_BASE_URL}/${path}`
  return `${API_BASE_URL}${path}`
}

let refreshInFlight: Promise<void> | null = null

export async function httpJson<T>(path: string, options: FetchJsonOptions = {}): Promise<T> {
  const auth = useAuthStore()
  const accessToken = auth.accessToken

  try {
    return await baseFetchJson<T>(withBase(path), {
      ...options,
      headers: {
        ...(options.headers ?? {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    })
  } catch (err) {
    if (!(err instanceof ApiError)) throw err
    if (err.status !== 401) throw err

    if (!auth.refreshToken) throw err

    refreshInFlight ??= auth.refresh().finally(() => {
      refreshInFlight = null
    })
    await refreshInFlight

    const newAccessToken = auth.accessToken
    if (!newAccessToken) throw err

    return await baseFetchJson<T>(withBase(path), {
      ...options,
      headers: {
        ...(options.headers ?? {}),
        Authorization: `Bearer ${newAccessToken}`,
      },
    })
  }
}

