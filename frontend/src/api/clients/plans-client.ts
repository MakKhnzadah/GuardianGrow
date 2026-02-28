import { httpJson } from '../http'

export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'

export type PlanItem = {
  id: string
  contentId: string
  dayOfWeek: DayOfWeek
  targetMinutes: number
  orderIndex: number
  allowedStart?: string | null
  allowedEnd?: string | null
}

export type Plan = {
  id: string
  childId: string
  weekStart: string
  dailyTimeLimitMin: number
  breakAfterMin: number
  breakDurationMin: number
  status: 'ACTIVE' | 'ARCHIVED'
  items: PlanItem[]
}

export async function getPlan(childId: string, params: { weekStart?: string } = {}) {
  const qs = params.weekStart ? `?weekStart=${encodeURIComponent(params.weekStart)}` : ''
  return await httpJson<Plan>(`/children/${childId}/plan${qs}`, { method: 'GET' })
}

export async function addPlanItem(
  childId: string,
  input: { contentId: string; dayOfWeek: DayOfWeek; targetMinutes: number; allowedStart?: string; allowedEnd?: string },
  params: { weekStart?: string } = {},
) {
  const qs = params.weekStart ? `?weekStart=${encodeURIComponent(params.weekStart)}` : ''
  return await httpJson<PlanItem>(`/children/${childId}/plan/items${qs}`, {
    method: 'POST',
    body: input,
  })
}
