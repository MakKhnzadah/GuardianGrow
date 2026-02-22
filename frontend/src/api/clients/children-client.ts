import { httpJson } from '../http'

export type ChildProfile = {
  id: string
  displayName: string
  birthDate: string
  avatarKey?: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt?: string
}

export async function listChildren(householdId: string) {
  return await httpJson<ChildProfile[]>(`/households/${householdId}/children`, { method: 'GET' })
}

export async function createChild(
  householdId: string,
  input: { displayName: string; birthDate: string; avatarKey?: string },
) {
  return await httpJson<ChildProfile>(`/households/${householdId}/children`, {
    method: 'POST',
    body: input,
  })
}
