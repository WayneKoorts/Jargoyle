import type { SuggestedQuestion } from '../../api/conversations'

interface SuggestedQuestionsProps {
  questions: SuggestedQuestion[]
  onSelect: (questionText: string) => void
}

/**
 * Grid of pill-shaped buttons showing suggested questions for a new conversation.
 *
 * Questions are grouped by category (e.g. "General", "Costs") so the user
 * can scan related questions together. Clicking a pill sends that question
 * as the first message in the conversation.
 */
export default function SuggestedQuestions({ questions, onSelect }: SuggestedQuestionsProps) {
  if (questions.length === 0) return null

  // Group questions by category, preserving insertion order
  const grouped = new Map<string, SuggestedQuestion[]>()
  for (const q of questions) {
    const existing = grouped.get(q.category) ?? []
    existing.push(q)
    grouped.set(q.category, existing)
  }

  return (
    <div className="mx-auto max-w-md space-y-5 px-4 py-6">
      <p className="text-centre text-sm text-slate-500">
        Here are some questions to get you started:
      </p>

      {[...grouped.entries()].map(([category, categoryQuestions]) => (
        <div key={category}>
          <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
            {category}
          </h4>
          <div className="flex flex-wrap gap-2">
            {categoryQuestions.map((q) => (
              <button
                key={q.text}
                onClick={() => onSelect(q.text)}
                className="rounded-full border border-indigo-200 bg-white px-4 py-2 text-sm text-indigo-700 transition-colors hover:bg-indigo-50"
              >
                {q.text}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
