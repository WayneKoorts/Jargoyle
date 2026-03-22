import { render, screen } from '@testing-library/react'
import SourceChunkDisplay from './SourceChunkDisplay'
import type { SourceChunkReference } from '../../api/conversations'

const sampleChunks: SourceChunkReference[] = [
  { chunkId: 'chunk-1', chunkIndex: 0, preview: 'Your monthly electricity usage...' },
  { chunkId: 'chunk-2', chunkIndex: 3, preview: 'The total amount due is...' },
]

describe('SourceChunkDisplay', () => {
  it('renders one tag per source chunk with human-friendly numbering', () => {
    render(<SourceChunkDisplay sourceChunks={sampleChunks} />)

    expect(screen.getByText('Section 1')).toBeInTheDocument()
    expect(screen.getByText('Section 4')).toBeInTheDocument()
  })

  it('shows preview text as a tooltip on each tag', () => {
    render(<SourceChunkDisplay sourceChunks={sampleChunks} />)

    const section1 = screen.getByText('Section 1')
    expect(section1).toHaveAttribute('title', 'Your monthly electricity usage...')

    const section4 = screen.getByText('Section 4')
    expect(section4).toHaveAttribute('title', 'The total amount due is...')
  })

  it('renders nothing when given an empty array', () => {
    const { container } = render(<SourceChunkDisplay sourceChunks={[]} />)
    expect(container.firstChild).toBeNull()
  })
})
