import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StageDiagnosisCard from './StageDiagnosisCard.vue'

describe('StageDiagnosisCard.vue', () => {
    it('should render correct health score', () => {
        const wrapper = mount(StageDiagnosisCard, {
            props: { performanceScore: 85, diagnosisInfo: '[]' }
        })
        expect(wrapper.find('.score-value').text()).toBe('85')
        expect(wrapper.find('.score-circle').classes()).toContain('good')
    })

    it('should show "Pending" when performanceScore is null', () => {
        const wrapper = mount(StageDiagnosisCard, {
            props: { performanceScore: null, diagnosisInfo: '[]' }
        })
        expect(wrapper.find('.score-value').text()).toBe('Pending')
        expect(wrapper.find('.no-issues').text()).toContain('Metrics are being aggregated')
    })

    it('should display warning color for medium scores', () => {
        const wrapper = mount(StageDiagnosisCard, {
            props: { performanceScore: 50, diagnosisInfo: '[]' }
        })
        expect(wrapper.find('.score-circle').classes()).toContain('warning')
    })

    it('should display critical color for low scores', () => {
        const wrapper = mount(StageDiagnosisCard, {
            props: { performanceScore: 20, diagnosisInfo: '[]' }
        })
        expect(wrapper.find('.score-circle').classes()).toContain('critical')
    })

    it('should parse and display dimension scores', () => {
        const diagnosisData = JSON.stringify([
            { dimension: 'GC Impact', score: 30 },
            { dimension: 'Data Skew', score: 95 }
        ])
        const wrapper = mount(StageDiagnosisCard, {
            props: { performanceScore: 60, diagnosisInfo: diagnosisData }
        })
        
        const dimensionItems = wrapper.findAll('.dimension-item')
        expect(dimensionItems.length).toBeGreaterThan(0)
        
        // Find GC Impact item
        const gcItem = dimensionItems.find(item => item.text().includes('GC Impact'))
        expect(gcItem).toBeDefined()
        expect(gcItem?.find('.dim-score').text()).toBe('30')
        // Check dot color (critical)
        const dot = gcItem?.find('.status-dot')
        expect(dot?.attributes('style')).toContain('background-color: rgb(231, 76, 60)') // #e74c3c
    })
})
