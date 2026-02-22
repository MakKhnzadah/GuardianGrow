import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useSessionMachine } from './useSessionMachine'

vi.mock('../../api/clients/sessions-client', () => {
  return {
    startSession: vi.fn(async () => ({
      sessionId: 's1',
      state: 'ACTIVE',
      hardStopAt: new Date(Date.now() + 2000).toISOString(),
      breakPolicy: { breakAfterMin: 15, breakDurationMin: 5 },
      content: { contentId: 'c1', title: 'Test Content' },
    })),
    heartbeat: vi.fn(async () => ({ state: 'ACTIVE' })),
    endSession: vi.fn(async () => undefined),
    postEvent: vi.fn(async () => undefined),
  }
})

describe('useSessionMachine', () => {
  it('auto-ends when hard stop reached', async () => {
    vi.useFakeTimers()

    const Test = defineComponent({
      name: 'TestMachine',
      setup() {
        return { machine: useSessionMachine({ childId: 'child1', planItemId: 'pi1' }) }
      },
      template: '<div />',
    })

    const wrapper = mount(Test, {
      global: {
        plugins: [createPinia()],
      },
    })

    await wrapper.vm.machine.start()
    expect(wrapper.vm.machine.sessionId.value).toBe('s1')
    expect(wrapper.vm.machine.state.value).toBe('ACTIVE')

    await nextTick()
    vi.advanceTimersByTime(3000)
    await vi.runAllTimersAsync()
    await nextTick()

    expect(wrapper.vm.machine.state.value).toBe('ENDED')

    vi.useRealTimers()
  })
})
