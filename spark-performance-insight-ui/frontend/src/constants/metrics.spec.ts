import { describe, it, expect } from 'vitest'
import { AVAILABLE_METRICS, DEFAULT_METRICS } from './metrics'

describe('metrics constants', () => {
    it('should have AVAILABLE_METRICS defined with correct structure', () => {
        expect(AVAILABLE_METRICS).toBeDefined()
        expect(Array.isArray(AVAILABLE_METRICS)).toBe(true)
        expect(AVAILABLE_METRICS.length).toBeGreaterThan(0)
        
        AVAILABLE_METRICS.forEach(metric => {
            expect(metric).toHaveProperty('key')
            expect(metric).toHaveProperty('label')
            expect(metric).toHaveProperty('type')
            if (metric.type === 'composite') {
                expect(metric).toHaveProperty('subKeys')
                expect(Array.isArray(metric.subKeys)).toBe(true)
            }
        })
    })

    it('should have DEFAULT_METRICS matching AVAILABLE_METRICS keys', () => {
        expect(DEFAULT_METRICS).toBeDefined()
        expect(DEFAULT_METRICS.length).toBe(AVAILABLE_METRICS.length)
        AVAILABLE_METRICS.forEach(m => {
            expect(DEFAULT_METRICS).toContain(m.key)
        })
    })
})
