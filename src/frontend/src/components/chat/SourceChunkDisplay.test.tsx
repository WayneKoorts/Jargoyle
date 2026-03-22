import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SourceChunkDisplay from './SourceChunkDisplay'
import type { SourceChunkReference } from '../../api/conversations'

const sampleChunks: SourceChunkReference[] = [
  { chunkId: 'chunk-1', chunkIndex: 0, preview: 'Your monthly electricity usage...' },
  { chunkId: 'chunk-2', chunkIndex: 3, preview: 'The total amount due is...' },
]

/** Creates an array of N source chunks for testing the collapse threshold. */
function makeManyChunks(count: number): SourceChunkReference[] {
  return Array.from({ length: count }, (_, i) => ({
    chunkId: `chunk-${i}`,
    chunkIndex: i,
    preview: `Preview for chunk ${i}`,
  }))
}

describe('SourceChunkDisplay', () => {
  it('renders one tag per source chunk with human-friendly numbering', () => {
    render(<SourceChunkDisplay sourceChunks={sampleChunks} />)

    expect(screen.getByText('Section 1')).toBeInTheDocument()
    expect(screen.getByText('Section 4')).toBeInTheDocument()
  })

  it('shows tooltip with preview text on hover', async () => {
    const user = userEvent.setup()
    render(<SourceChunkDisplay sourceChunks={sampleChunks} />)

    // Tooltip not visible before hover
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    // Hover over the first pill
    await user.hover(screen.getByText('Section 1'))

    const tooltip = screen.getByRole('tooltip')
    expect(tooltip).toHaveTextContent('Your monthly electricity usage...')
  })

  it('links pill to its tooltip via aria-describedby on hover', async () => {
    const user = userEvent.setup()
    render(<SourceChunkDisplay sourceChunks={sampleChunks} />)

    const section1 = screen.getByText('Section 1')
    expect(section1).not.toHaveAttribute('aria-describedby')

    await user.hover(section1)
    expect(section1).toHaveAttribute('aria-describedby', 'tooltip-chunk-1')
  })

  it('hides tooltip when mouse leaves the pill', async () => {
    const user = userEvent.setup()
    render(<SourceChunkDisplay sourceChunks={sampleChunks} />)

    await user.hover(screen.getByText('Section 1'))
    expect(screen.getByRole('tooltip')).toBeInTheDocument()

    await user.unhover(screen.getByText('Section 1'))
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('renders nothing when given an empty array', () => {
    const { container } = render(<SourceChunkDisplay sourceChunks={[]} />)
    expect(container.firstChild).toBeNull()
  })

  describe('collapsible behaviour', () => {
    it('shows pills inline when at or below the threshold', () => {
      const chunks = makeManyChunks(5)
      render(<SourceChunkDisplay sourceChunks={chunks} />)

      // All pills visible, no toggle
      expect(screen.getByText('Section 1')).toBeInTheDocument()
      expect(screen.getByText('Section 5')).toBeInTheDocument()
      expect(screen.queryByRole('button')).not.toBeInTheDocument()
    })

    it('collapses pills behind a toggle when above the threshold', () => {
      const chunks = makeManyChunks(10)
      render(<SourceChunkDisplay sourceChunks={chunks} />)

      // Pills are hidden
      expect(screen.queryByText('Section 1')).not.toBeInTheDocument()

      // Summary toggle is shown with the correct count
      expect(screen.getByRole('button', { name: /referenced 10 sections/i })).toBeInTheDocument()
    })

    it('expands pills when the toggle is clicked', async () => {
      const user = userEvent.setup()
      const chunks = makeManyChunks(8)
      render(<SourceChunkDisplay sourceChunks={chunks} />)

      // Click to expand
      await user.click(screen.getByRole('button', { name: /referenced 8 sections/i }))

      // Pills are now visible
      expect(screen.getByText('Section 1')).toBeInTheDocument()
      expect(screen.getByText('Section 8')).toBeInTheDocument()
    })

    it('collapses pills again when the expanded toggle is clicked', async () => {
      const user = userEvent.setup()
      const chunks = makeManyChunks(8)
      render(<SourceChunkDisplay sourceChunks={chunks} />)

      // Expand
      await user.click(screen.getByRole('button', { name: /referenced 8 sections/i }))
      expect(screen.getByText('Section 1')).toBeInTheDocument()

      // Collapse — click the toggle again (now shows down chevron)
      await user.click(screen.getByRole('button', { name: /referenced 8 sections/i }))
      expect(screen.queryByText('Section 1')).not.toBeInTheDocument()
    })
  })
})
