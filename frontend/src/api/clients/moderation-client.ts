import { httpJson } from '../http'

export type ModerationStatus = 'PENDING' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED'

export type ModerationQueueItem = {
  id: string
  contentId: string
  versionId: string
  status: ModerationStatus
  submittedAt: string
  submittedBy?: string | null
  reviewedAt?: string | null
  reviewedBy?: string | null
  reviewNotes?: string | null
}

export async function listQueue(params: { status?: ModerationStatus }) {
  const qs = params.status ? `?status=${encodeURIComponent(params.status)}` : ''
  return await httpJson<ModerationQueueItem[]>(`/moderation/queue${qs}`, { method: 'GET' })
}

export async function getQueueItem(queueItemId: string) {
  return await httpJson<ModerationQueueItem>(`/moderation/items/${queueItemId}`, { method: 'GET' })
}

export async function reviewQueueItem(queueItemId: string, input: { decision: string; notes?: string }) {
  return await httpJson<void>(`/moderation/items/${queueItemId}/review`, {
    method: 'POST',
    body: input,
  })
}

export async function publishContent(contentId: string, input?: { versionId?: string }) {
  return await httpJson<void>(`/content/${contentId}/publish`, {
    method: 'POST',
    body: input ?? {},
  })
}
