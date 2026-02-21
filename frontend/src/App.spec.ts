import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.hoisted(() => {
  const store: Record<string, string> = {}
  const localStorageMock = {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => { store[key] = value.toString() },
    clear: () => { for (const key in store) delete store[key] },
    removeItem: (key: string) => { delete store[key] },
    length: 0,
    key: (index: number) => null
  }
  Object.defineProperty(global, 'localStorage', { value: localStorageMock })
})

vi.mock('./api', () => ({
  validateCompareItems: vi.fn(() => Promise.resolve({ data: {} }))
}))

import App from './App.vue'

describe('App.vue Global UI Guard', () => {
    const globalConfig = {
        stubs: {
            'router-link': true,
            'router-view': true
        }
    }

    it('should render the navigation bar with brand name', () => {
        const wrapper = mount(App, { global: globalConfig })
        expect(wrapper.find('.navbar').exists()).toBe(true)
        expect(wrapper.text()).toContain('Spark Performance Insight')
    })

    it('should render the global footer with contact and github links', () => {
        const wrapper = mount(App, { global: globalConfig })
        const footer = wrapper.find('.footer')
        expect(footer.exists()).toBe(true)
        
        // Contact guard
        expect(footer.find('a[href^="mailto:"]').exists()).toBe(true)
        
        // GitHub guard
        expect(footer.find('a[href*="github.com"]').exists()).toBe(true)
    })
})
