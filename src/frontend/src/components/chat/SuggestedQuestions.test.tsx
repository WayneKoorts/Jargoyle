import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SuggestedQuestions from './SuggestedQuestions'
import type { SuggestedQuestion } from '../../api/conversations'

const sampleQuestions: SuggestedQuestion[] = [
  { text: 'What is the main topic?', category: 'General' },
  { text: 'Who are the parties involved?', category: 'General' },
  { text: 'Are there any costs mentioned?', category: 'Costs' },
]

describe('SuggestedQuestions', () => {
  it('renders all questions as buttons', () => {
    render(<SuggestedQuestions questions={sampleQuestions} onSelect={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'What is the main topic?' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Who are the parties involved?' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Are there any costs mentioned?' })).toBeInTheDocument()
  })

  it('groups questions by category with headings', () => {
    render(<SuggestedQuestions questions={sampleQuestions} onSelect={vi.fn()} />)

    expect(screen.getByText('General')).toBeInTheDocument()
    expect(screen.getByText('Costs')).toBeInTheDocument()
  })

  it('calls onSelect with the question text when a pill is clicked', async () => {
    const onSelect = vi.fn()
    render(<SuggestedQuestions questions={sampleQuestions} onSelect={onSelect} />)

    await userEvent.click(screen.getByRole('button', { name: 'Are there any costs mentioned?' }))

    expect(onSelect).toHaveBeenCalledOnce()
    expect(onSelect).toHaveBeenCalledWith('Are there any costs mentioned?')
  })

  it('renders nothing when questions array is empty', () => {
    const { container } = render(<SuggestedQuestions questions={[]} onSelect={vi.fn()} />)
    expect(container.firstChild).toBeNull()
  })

  it('shows introductory text', () => {
    render(<SuggestedQuestions questions={sampleQuestions} onSelect={vi.fn()} />)
    expect(screen.getByText('Here are some questions to get you started:')).toBeInTheDocument()
  })
})
