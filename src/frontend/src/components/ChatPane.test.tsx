import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ChatPane from './ChatPane'

describe('ChatPane', () => {
  it('panel is visible (no translate) when open', () => {
    const { container } = render(<ChatPane open={true} onClose={vi.fn()} />)

    // The slide-in panel should have translate-x-0 when open
    const panel = container.querySelector('.translate-x-0')
    expect(panel).toBeInTheDocument()
  })

  it('panel is hidden (translated off-screen) when closed', () => {
    const { container } = render(<ChatPane open={false} onClose={vi.fn()} />)

    // The slide-in panel should have translate-x-full when closed
    const panel = container.querySelector('.translate-x-full')
    expect(panel).toBeInTheDocument()
  })

  it('backdrop click calls onClose', async () => {
    const onClose = vi.fn()
    const { container } = render(<ChatPane open={true} onClose={onClose} />)

    // The backdrop is the first div with the fixed inset-0 class
    const backdrop = container.querySelector('.fixed.inset-0.z-40')
    expect(backdrop).toBeInTheDocument()

    await userEvent.click(backdrop!)
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('close button calls onClose', async () => {
    const onClose = vi.fn()
    render(<ChatPane open={true} onClose={onClose} />)

    // The close button is inside the panel header
    const buttons = screen.getAllByRole('button')
    // First button in the panel is the close button (the X)
    await userEvent.click(buttons[0])
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('input and send button are disabled', () => {
    render(<ChatPane open={true} onClose={vi.fn()} />)

    const input = screen.getByPlaceholderText('Type a question…')
    expect(input).toBeDisabled()

    const sendButton = screen.getByRole('button', { name: 'Send' })
    expect(sendButton).toBeDisabled()
  })
})
