import { render, screen } from '@testing-library/react'
import FormattedContent from './FormattedContent'

describe('FormattedContent', () => {
  it('renders plain text as a paragraph', () => {
    render(<FormattedContent text="Hello world" />)
    expect(screen.getByText('Hello world')).toBeInTheDocument()
  })

  it('renders **bold** text with a strong element', () => {
    const { container } = render(<FormattedContent text="This is **important** text" />)
    const strong = container.querySelector('strong')
    expect(strong).toBeInTheDocument()
    expect(strong?.textContent).toBe('important')
  })

  it('renders *italic* text with an em element', () => {
    const { container } = render(<FormattedContent text="This is *emphasised* text" />)
    const em = container.querySelector('em')
    expect(em).toBeInTheDocument()
    expect(em?.textContent).toBe('emphasised')
  })

  it('renders `code` text with a code element', () => {
    const { container } = render(<FormattedContent text="Use the `forEach` method" />)
    const code = container.querySelector('code')
    expect(code).toBeInTheDocument()
    expect(code?.textContent).toBe('forEach')
  })

  it('renders list items starting with "- "', () => {
    render(<FormattedContent text={'Items:\n- First\n- Second\n- Third'} />)
    expect(screen.getByText('First')).toBeInTheDocument()
    expect(screen.getByText('Second')).toBeInTheDocument()
    expect(screen.getByText('Third')).toBeInTheDocument()
    // Bullet characters
    expect(screen.getAllByText('•')).toHaveLength(3)
  })

  it('renders bold within list items', () => {
    const { container } = render(<FormattedContent text={'- **Xero** — current role'} />)
    const strong = container.querySelector('strong')
    expect(strong).toBeInTheDocument()
    expect(strong?.textContent).toBe('Xero')
  })

  it('handles multiple formatting types in one line', () => {
    const { container } = render(<FormattedContent text="The **total** is `£150` per *month*" />)
    expect(container.querySelector('strong')?.textContent).toBe('total')
    expect(container.querySelector('code')?.textContent).toBe('£150')
    expect(container.querySelector('em')?.textContent).toBe('month')
  })

  it('renders nested list items with indentation and different bullet', () => {
    render(<FormattedContent text={'Skills:\n- Technical:\n  - languages\n  - frameworks\n- Soft skills'} />)
    expect(screen.getByText('Technical:')).toBeInTheDocument()
    expect(screen.getByText('languages')).toBeInTheDocument()
    expect(screen.getByText('frameworks')).toBeInTheDocument()
    expect(screen.getByText('Soft skills')).toBeInTheDocument()
    // Top-level bullets use •, nested use ◦
    expect(screen.getAllByText('•')).toHaveLength(2)
    expect(screen.getAllByText('◦')).toHaveLength(2)
  })

  it('preserves empty lines as spacing', () => {
    const { container } = render(<FormattedContent text={'Line one\n\nLine two'} />)
    // Empty line produces a spacing div
    const spacers = container.querySelectorAll('.h-2')
    expect(spacers).toHaveLength(1)
  })
})
