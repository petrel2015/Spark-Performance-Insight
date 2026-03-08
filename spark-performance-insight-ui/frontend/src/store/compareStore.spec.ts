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

  it('should toggle compare mode', () => {
    const initialMode = compareStore.isCompareMode
    compareStore.toggleCompareMode()
    expect(compareStore.isCompareMode).toBe(!initialMode)
    compareStore.toggleCompareMode()
    expect(compareStore.isCompareMode).toBe(initialMode)
  })

  it('should check if item is selected', () => {
    const key = 'app-1:app:app-1'
    compareStore.addItem({ type: 'app', appId: 'app-1', itemId: 'app-1' })
    compareStore.toggleComparisonItem(key, true)
    expect(compareStore.isItemSelected(key)).toBe(true)
    compareStore.toggleComparisonItem(key, false)
    expect(compareStore.isItemSelected(key)).toBe(false)
  })

  it('should mark app as invalid', () => {
    const key1 = 'app-1:job:1'
    const key2 = 'app-1:job:2'
    const key3 = 'app-2:job:1'
    
    compareStore.addItem({ type: 'job', appId: 'app-1', itemId: 1 })
    compareStore.addItem({ type: 'job', appId: 'app-1', itemId: 2 })
    compareStore.addItem({ type: 'job', appId: 'app-2', itemId: 1 })
    
    compareStore.toggleComparisonItem(key1, true)
    compareStore.toggleComparisonItem(key3, true)
    
    compareStore.markAppAsInvalid('app-1')
    
    const item1 = compareStore.selectedItems.find(i => i.id === key1)
    const item2 = compareStore.selectedItems.find(i => i.id === key2)
    const item3 = compareStore.selectedItems.find(i => i.id === key3)
    
    expect(item1?.isInvalid).toBe(true)
    expect(item2?.isInvalid).toBe(true)
    expect(item3?.isInvalid).toBe(false)
    
    expect(compareStore.comparisonSelection).not.toContain(key1)
    expect(compareStore.comparisonSelection).toContain(key3)
  })

  it('should validate all items', async () => {
    const key1 = 'app-1:job:1'
    const key2 = 'app-2:job:1'
    
    compareStore.addItem({ type: 'job', appId: 'app-1', itemId: 1 })
    compareStore.addItem({ type: 'job', appId: 'app-2', itemId: 1 })
    
    compareStore.toggleComparisonItem(key1, true)
    compareStore.toggleComparisonItem(key2, true)
    
    const { validateCompareItems } = await import('../api')
    vi.mocked(validateCompareItems).mockResolvedValueOnce({
      data: {
        [key1]: true,
        [key2]: false
      }
    } as any)
    
    await compareStore.validateAllItems()
    
    const item1 = compareStore.selectedItems.find(i => i.id === key1)
    const item2 = compareStore.selectedItems.find(i => i.id === key2)
    
    expect(item1?.isInvalid).toBe(false)
    expect(item2?.isInvalid).toBe(true)
    
    expect(compareStore.comparisonSelection).toContain(key1)
    expect(compareStore.comparisonSelection).not.toContain(key2)
  })
})
