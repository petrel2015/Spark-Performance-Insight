import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CollapsibleCard from './CollapsibleCard.vue'

describe('CollapsibleCard.vue', () => {
    it('should render title correctly', () => {
        const wrapper = mount(CollapsibleCard, {
            props: { title: 'My Title' }
        })
        expect(wrapper.text()).toContain('My Title')
    })

    it('should toggle content when header is clicked', async () => {
        const wrapper = mount(CollapsibleCard, {
            props: { title: 'Toggle Me' },
            slots: { default: '<div class="test-content">Secret Content</div>' }
        })
        
        // Initially expanded (exists)
        expect(wrapper.find('.card-content').exists()).toBe(true)
        
        // Click header to collapse
        await wrapper.find('.card-header').trigger('click')
        expect(wrapper.find('.card-content').exists()).toBe(false)
        
        // Click header again to expand
        await wrapper.find('.card-header').trigger('click')
        expect(wrapper.find('.card-content').exists()).toBe(true)
    })

    it('should start collapsed if initialCollapsed is true', () => {
        const wrapper = mount(CollapsibleCard, {
            props: { title: 'Start Collapsed', initialCollapsed: true },
            slots: { default: '<div class="test-content">Secret Content</div>' }
        })
        expect(wrapper.find('.card-content').exists()).toBe(false)
    })
})
