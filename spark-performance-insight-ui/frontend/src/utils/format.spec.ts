import { describe, it, expect } from 'vitest'
import { formatTime, formatBytes, formatCompactNum, formatNum, formatDateTime } from './format'

describe('format utils', () => {
    describe('formatTime', () => {
        it('should format milliseconds correctly', () => {
            expect(formatTime(null)).toBe('-')
            expect(formatTime(undefined)).toBe('-')
            expect(formatTime(-1)).toBe('-')
            expect(formatTime(0)).toBe('0ms')
            expect(formatTime(500.2)).toBe('500ms')
        })

        it('should format seconds correctly', () => {
            expect(formatTime(1500)).toBe('1.5s')
            expect(formatTime(59000)).toBe('59.0s')
        })

        it('should format minutes correctly', () => {
            expect(formatTime(61000)).toBe('1m 1s')
            expect(formatTime(3540000)).toBe('59m 0s')
        })

        it('should format hours correctly', () => {
            expect(formatTime(3661000)).toBe('1h 1m 1s')
        })

        it('should format days correctly', () => {
            expect(formatTime(86400000 + 3600000)).toBe('1d 1h 0m')
        })
    })

    describe('formatBytes', () => {
        it('should format bytes correctly', () => {
            expect(formatBytes(null)).toBe('-')
            expect(formatBytes(0)).toBe('-')
            expect(formatBytes(512)).toBe('512 B')
            expect(formatBytes(1024)).toBe('1 KB')
            expect(formatBytes(1536)).toBe('1.5 KB')
            expect(formatBytes(1048576)).toBe('1 MB')
        })
    })

    describe('formatCompactNum', () => {
        it('should format numbers correctly', () => {
            expect(formatCompactNum(0)).toBe('')
            expect(formatCompactNum(500)).toBe('500')
            expect(formatCompactNum(1500)).toBe('1.5 K')
            expect(formatCompactNum(2000000)).toBe('2.0 M')
        })
    })

    describe('formatDateTime', () => {
        it('should format dates correctly', () => {
            expect(formatDateTime(null)).toBe('-')
            expect(formatDateTime(undefined)).toBe('-')
            expect(formatDateTime('')).toBe('-')
            // Using a fixed timestamp to test (e.g. 2024-03-09 12:00:00 UTC)
            const t = new Date('2024-03-09T12:00:00Z').getTime()
            const formatted = formatDateTime(t)
            expect(formatted).not.toBe('-')
            // Don't test exact string due to locale differences
        })
    })
})
