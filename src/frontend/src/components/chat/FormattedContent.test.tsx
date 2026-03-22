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

  it('renders unordered list items', () => {
    const { container } = render(
      <FormattedContent text={'Items:\n- First\n- Second\n- Third'} />,
    )
    const listItems = container.querySelectorAll('li')
    expect(listItems).toHaveLength(3)
    expect(screen.getByText('First')).toBeInTheDocument()
    expect(screen.getByText('Second')).toBeInTheDocument()
    expect(screen.getByText('Third')).toBeInTheDocument()
  })

  it('renders bold within list items', () => {
    const { container } = render(<FormattedContent text={'- **Xero** — current role'} />)
    const strong = container.querySelector('strong')
    expect(strong).toBeInTheDocument()
    expect(strong?.textContent).toBe('Xero')
  })

  it('handles multiple formatting types in one line', () => {
    const { container } = render(
      <FormattedContent text="The **total** is `£150` per *month*" />,
    )
    expect(container.querySelector('strong')?.textContent).toBe('total')
    expect(container.querySelector('code')?.textContent).toBe('£150')
    expect(container.querySelector('em')?.textContent).toBe('month')
  })

  it('renders nested list items', () => {
    const { container } = render(
      <FormattedContent
        text={'Skills:\n- Technical:\n  - languages\n  - frameworks\n- Soft skills'}
      />,
    )
    const nestedList = container.querySelector('ul ul')
    expect(nestedList).toBeInTheDocument()
    expect(screen.getByText('languages')).toBeInTheDocument()
    expect(screen.getByText('frameworks')).toBeInTheDocument()
  })

  it('renders headings with appropriate elements', () => {
    const { container } = render(
      <FormattedContent text={'# Heading 1\n\n## Heading 2\n\n### Heading 3'} />,
    )
    expect(container.querySelector('h1')?.textContent).toBe('Heading 1')
    expect(container.querySelector('h2')?.textContent).toBe('Heading 2')
    expect(container.querySelector('h3')?.textContent).toBe('Heading 3')
  })

  it('renders fenced code blocks in a pre element', () => {
    const { container } = render(
      <FormattedContent text={'```javascript\nconst x = 1;\n```'} />,
    )
    const pre = container.querySelector('pre')
    expect(pre).toBeInTheDocument()
    expect(pre?.textContent).toContain('const x = 1;')
  })

  it('renders tables with a scrollable wrapper', () => {
    const markdown = '| Name | Age |\n| --- | --- |\n| Alice | 30 |\n| Bob | 25 |'
    const { container } = render(<FormattedContent text={markdown} />)

    const table = container.querySelector('table')
    expect(table).toBeInTheDocument()

    // Table should be inside a scrollable wrapper div
    const wrapper = table?.parentElement
    expect(wrapper?.classList.contains('overflow-x-auto')).toBe(true)

    // Check header cells
    const headers = container.querySelectorAll('th')
    expect(headers).toHaveLength(2)
    expect(headers[0].textContent).toBe('Name')
    expect(headers[1].textContent).toBe('Age')

    // Check data cells
    const cells = container.querySelectorAll('td')
    expect(cells).toHaveLength(4)
  })

  it('renders ordered lists with numbers', () => {
    const { container } = render(
      <FormattedContent text={'1. First\n2. Second\n3. Third'} />,
    )
    const ol = container.querySelector('ol')
    expect(ol).toBeInTheDocument()
    const listItems = ol?.querySelectorAll('li')
    expect(listItems).toHaveLength(3)
  })

  it('renders blockquotes', () => {
    const { container } = render(
      <FormattedContent text={'> This is a quote'} />,
    )
    const blockquote = container.querySelector('blockquote')
    expect(blockquote).toBeInTheDocument()
    expect(blockquote?.textContent).toContain('This is a quote')
  })

  it('renders horizontal rules', () => {
    const { container } = render(
      <FormattedContent text={'Above\n\n---\n\nBelow'} />,
    )
    const hr = container.querySelector('hr')
    expect(hr).toBeInTheDocument()
  })

  it('renders ~~strikethrough~~ text', () => {
    const { container } = render(
      <FormattedContent text={'This is ~~deleted~~ text'} />,
    )
    const del = container.querySelector('del')
    expect(del).toBeInTheDocument()
    expect(del?.textContent).toBe('deleted')
  })

  it('renders links with target="_blank"', () => {
    const { container } = render(
      <FormattedContent text={'Visit [Example](https://example.com)'} />,
    )
    const link = container.querySelector('a')
    expect(link).toBeInTheDocument()
    expect(link?.textContent).toBe('Example')
    expect(link?.getAttribute('href')).toBe('https://example.com')
    expect(link?.getAttribute('target')).toBe('_blank')
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer')
  })
})
