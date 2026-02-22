import { httpJson } from '../http'

export type ContentType = 'LESSON' | 'STORY' | 'PUZZLE'

export type ContentListItem = {
  id: string
  type: ContentType
  title: string
  topic?: string | null
  minAge: number
  maxAge: number
  difficulty?: number | null
  estMinutes?: number | null
  status: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'
}

export type ContentSearchParams = {
  age?: number
  topic?: string
  type?: ContentType
  difficulty?: number
  minDuration?: number
  maxDuration?: number
  q?: string
  page?: number
  pageSize?: number
  sort?: string
}

export function buildContentQueryString(params: ContentSearchParams) {
  const sp = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    sp.set(key, String(value))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

export async function searchContent(params: ContentSearchParams) {
  return await httpJson<ContentListItem[]>(`/content${buildContentQueryString(params)}`, { method: 'GET' })
}

export async function getContent(contentId: string) {
  return await httpJson<ContentListItem & { body?: unknown }>(`/content/${contentId}`, { method: 'GET' })
}
