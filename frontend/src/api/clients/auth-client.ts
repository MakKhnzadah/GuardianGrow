import { baseFetchJson } from '../base-fetch'
import { API_BASE_URL } from '../config'

export type AuthResponse = {
  userId: string
  householdId: string
  accessToken: string
  refreshToken: string
}

export type RefreshResponse = {
  accessToken: string
  refreshToken: string
}

export async function register(input: { email: string; password: string; displayName: string }) {
  return await baseFetchJson<AuthResponse>(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    body: input,
  })
}

export async function login(input: { email: string; password: string }) {
  return await baseFetchJson<AuthResponse>(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    body: input,
  })
}

export async function refresh(input: { refreshToken: string }) {
  return await baseFetchJson<RefreshResponse>(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    body: input,
  })
}

export async function logout(input: { refreshToken: string }) {
  return await baseFetchJson<void>(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    body: input,
  })
}

export async function me(accessToken: string) {
  return await baseFetchJson<{ userId: string; role: string; householdId?: string; email?: string; displayName?: string }>(
    `${API_BASE_URL}/me`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )
}
