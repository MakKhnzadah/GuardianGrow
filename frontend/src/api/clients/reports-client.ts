import { httpJson } from '../http'

export type ContentReportReason = 'INAPPROPRIATE' | 'INCORRECT' | 'OTHER'
export type ContentReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'

export type ContentReport = {
  id: string
  contentId: string
  reason: ContentReportReason
  details?: string | null
  status: ContentReportStatus
  createdAt: string
  resolvedAt?: string | null
}

export async function listContentReports(params: { status?: ContentReportStatus }) {
  const qs = params.status ? `?status=${encodeURIComponent(params.status)}` : ''
  return await httpJson<ContentReport[]>(`/reports/content${qs}`, { method: 'GET' })
}

export async function resolveContentReport(reportId: string, input: { resolution: 'RESOLVE' | 'DISMISS'; notes?: string }) {
  return await httpJson<void>(`/reports/content/${reportId}/resolve`, {
    method: 'POST',
    body: input,
  })
}
