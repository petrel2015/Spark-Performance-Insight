import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmationModal from './ConfirmationModal.vue'

describe('ConfirmationModal.vue', () => {
    it('should not be visible when isOpen prop is false', () => {
        const wrapper = mount(ConfirmationModal, {
            props: { isOpen: false, title: 'Confirm', message: 'Are you sure?' }
        })
        expect(wrapper.find('.modal-overlay').exists()).toBe(false)
    })

    it('should render title and message when isOpen is true', () => {
        const wrapper = mount(ConfirmationModal, {
            props: { isOpen: true, title: 'Custom Title', message: 'Custom Message' }
        })
        expect(wrapper.text()).toContain('Custom Title')
        expect(wrapper.text()).toContain('Custom Message')
    })

    it('should emit confirm when confirm button is clicked', async () => {
        const wrapper = mount(ConfirmationModal, {
            props: { isOpen: true, title: 'Confirm', message: '?' }
        })
        await wrapper.find('.btn.confirm').trigger('click')
        expect(wrapper.emitted()).toHaveProperty('confirm')
    })

    it('should emit cancel when cancel button is clicked', async () => {
        const wrapper = mount(ConfirmationModal, {
            props: { isOpen: true, title: 'Confirm', message: '?' }
        })
        
        await wrapper.find('.btn.cancel').trigger('click')
        expect(wrapper.emitted()).toHaveProperty('cancel')
    })
})
