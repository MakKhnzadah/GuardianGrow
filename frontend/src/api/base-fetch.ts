import { ApiError, type ProblemDetails } from './problem-details'

export type FetchJsonOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
}

async function safeJson(res: Response): Promise<unknown | undefined> {
  const ct = res.headers.get('content-type') || ''
  if (!ct.includes('application/json') && !ct.includes('application/problem+json')) return undefined
  try {
    return await res.json()
  } catch {
    return undefined
  }
}

export async function baseFetchJson<T>(url: string, options: FetchJsonOptions = {}): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (res.ok) {
    return (await safeJson(res)) as T
  }

  const problem = (await safeJson(res)) as ProblemDetails | undefined
  const message = problem?.title || problem?.detail || `Request failed (${res.status})`
  throw new ApiError(message, res.status, problem)
}
