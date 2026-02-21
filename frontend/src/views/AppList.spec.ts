import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// Comprehensive Mocks
vi.mock('../api', () => ({
  getApps: vi.fn(() => Promise.resolve({ data: { list: [], total: 0 } })),
  validateCompareItems: vi.fn(() => Promise.resolve({ data: {} }))
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} })
}))

vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation(() => ({
    close: vi.fn()
  }))
}))

vi.mock('stompjs', () => ({
  default: {
    over: vi.fn().mockImplementation(() => ({
      connect: vi.fn(),
      disconnect: vi.fn()
    }))
  }
}))

vi.hoisted(() => {
  const store: Record<string, string> = {}
  Object.defineProperty(global, 'localStorage', {
    value: {
      getItem: (key: string) => store[key] || null,
      setItem: (key: string, value: string) => { store[key] = value.toString() },
      clear: () => { for (const key in store) delete store[key] },
      removeItem: (key: string) => { delete store[key] }
    }
  })
})

import AppList from './AppList.vue'

describe('AppList.vue UI Regression Guard', () => {
    it('should always render the search input box', async () => {
        const wrapper = mount(AppList, {
            global: { stubs: { 'router-link': true } }
        })
        await new Promise(resolve => setTimeout(resolve, 0))
        const searchInput = wrapper.find('input.search-input')
        expect(searchInput.exists()).toBe(true)
    })
})
