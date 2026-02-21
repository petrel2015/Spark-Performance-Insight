import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// 1. Mock localStorage BEFORE importing anything
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

// 2. Mock API and Router
vi.mock('../api', () => ({
  getApps: vi.fn(() => Promise.resolve({ data: { list: [], total: 0 } })),
  validateCompareItems: vi.fn(() => Promise.resolve({ data: {} }))
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} })
}))

// 3. Now import the component
import AppList from './AppList.vue'

describe('AppList.vue UI Regression Guard', () => {
    it('should always render the search input box', () => {
        const wrapper = mount(AppList)
        const searchInput = wrapper.find('input.search-input')
        expect(searchInput.exists()).toBe(true)
        expect(searchInput.attributes('placeholder')).toContain('Search')
    })

    it('should always render the column selector', () => {
        const wrapper = mount(AppList)
        const columnSelector = wrapper.find('.column-selector-card')
        expect(columnSelector.exists()).toBe(true)
        
        const checkboxes = columnSelector.findAll('input[type="checkbox"]')
        expect(checkboxes.length).toBeGreaterThan(0)
    })

    it('should always render the pagination controls', async () => {
        const wrapper = mount(AppList)
        // Wait for potential microtasks
        await new Promise(resolve => setTimeout(resolve, 0))
        
        const pagination = wrapper.find('.modern-pagination')
        expect(pagination.exists()).toBe(true)
        
        expect(wrapper.find('button[title="Next Page"]').exists()).toBe(true)
        expect(wrapper.find('button[title="Previous Page"]').exists()).toBe(true)
    })
})
