import { describe, expect, it } from 'vitest'
import { buildContentQueryString } from './content-client'

describe('content-client', () => {
  it('buildContentQueryString omits empty values', () => {
    const qs = buildContentQueryString({
      q: '',
      topic: 'math',
      age: undefined,
      type: 'LESSON',
      minDuration: 5,
      maxDuration: 15,
    })

    expect(qs).toContain('topic=math')
    expect(qs).toContain('type=LESSON')
    expect(qs).toContain('minDuration=5')
    expect(qs).toContain('maxDuration=15')
    expect(qs).not.toContain('q=')
    expect(qs).not.toContain('age=')
  })
})
