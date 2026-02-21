import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.hoisted(() => {
  const store: Record<string, string> = {}
  const localStorageMock = {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString()
    },
    clear: () => {
      for (const key in store) delete store[key]
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    length: 0,
    key: (index: number) => null
  }
  Object.defineProperty(global, 'localStorage', { value: localStorageMock })
})

// 1. Mock API
vi.mock('../api', () => ({
  validateCompareItems: vi.fn()
}))

// 2. Now import the store
import { compareStore } from './compareStore'

describe('compareStore', () => {
  beforeEach(() => {
    compareStore.clear()
    localStorage.clear()
  })

  it('should add items correctly', () => {
    compareStore.addItem({ type: 'app', appId: 'app-1', itemId: 'app-1', name: 'Test App' })
    expect(compareStore.selectedItems).toHaveLength(1)
    expect(compareStore.selectedItems[0].id).toBe('app-1:app:app-1')
  })

  it('should not add duplicate items', () => {
    compareStore.addItem({ type: 'app', appId: 'app-1', itemId: 'app-1', name: 'Test App' })
    compareStore.addItem({ type: 'app', appId: 'app-1', itemId: 'app-1', name: 'Test App' })
    expect(compareStore.selectedItems).toHaveLength(1)
  })

  it('should remove items correctly', () => {
    compareStore.addItem({ type: 'app', appId: 'app-1', itemId: 'app-1', name: 'Test App' })
    const key = 'app-1:app:app-1'
    compareStore.removeItem(key)
    expect(compareStore.selectedItems).toHaveLength(0)
  })

  it('should toggle comparison selection', () => {
    const key = 'app-1:app:app-1'
    compareStore.addItem({ type: 'app', appId: 'app-1', itemId: 'app-1', name: 'Test App' })
    
    compareStore.toggleComparisonItem(key, true)
    expect(compareStore.comparisonSelection).toContain(key)
    
    compareStore.toggleComparisonItem(key, false)
    expect(compareStore.comparisonSelection).not.toContain(key)
  })

  it('should check if item is in workspace', () => {
    compareStore.addItem({ type: 'stage', appId: 'app-1', itemId: 5, name: 'Stage 5' })
    expect(compareStore.isInWorkspace('app-1', 'stage', 5)).toBe(true)
    expect(compareStore.isInWorkspace('app-1', 'stage', 6)).toBe(false)
  })
})
