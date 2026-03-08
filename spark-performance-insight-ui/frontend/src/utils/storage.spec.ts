import { describe, it, expect } from 'vitest'
import { parseStorageLevelObject, formatStorageLevel } from './storage'

describe('storage utils', () => {
    describe('parseStorageLevelObject', () => {
        it('should return null for invalid inputs', () => {
            expect(parseStorageLevelObject(null)).toBeNull()
            expect(parseStorageLevelObject('')).toBeNull()
        })

        it('should return fixed object for NONE', () => {
            const res = parseStorageLevelObject('NONE')
            expect(res.useMemory).toBe(false)
            expect(res.replication).toBe(0)
        })

        it('should parse JSON strings correctly', () => {
            const json = '{"useDisk":true,"useMemory":false,"useOffHeap":false,"deserialized":true,"replication":1}'
            const res = parseStorageLevelObject(json)
            expect(res.useDisk).toBe(true)
            expect(res.deserialized).toBe(true)
        })

        it('should return null for invalid JSON strings', () => {
            expect(parseStorageLevelObject('{invalid json')).toBeNull()
        })
    })

    describe('formatStorageLevel', () => {
        it('should return empty array for empty inputs', () => {
            expect(formatStorageLevel(null)).toEqual([])
        })

        it('should return ["None"] for NONE', () => {
            expect(formatStorageLevel('NONE')).toEqual(['None'])
        })

        it('should format JSON objects correctly', () => {
            const level = { useDisk: true, useMemory: true, useOffHeap: true, deserialized: true, replication: 2 }
            const res = formatStorageLevel(level)
            expect(res).toContain('Disk')
            expect(res).toContain('Memory')
            expect(res).toContain('OffHeap')
            expect(res).toContain('Deserialized')
            expect(res).toContain('2x Replicated')
        })

        it('should handle raw strings correctly', () => {
            expect(formatStorageLevel('MEMORY_ONLY')).toEqual(['MEMORY ONLY'])
        })

        it('should return ["Persisted"] for invalid JSON objects', () => {
            expect(formatStorageLevel('{invalid')).toEqual(['Persisted'])
        })
    })
})
