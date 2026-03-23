import { useConversations } from '../../hooks/useConversations'

interface ConversationSidebarProps {
  documentId: string
  activeConversationId: string | null
  onSelect: (conversationId: string) => void
  onNewConversation: () => void
}

/**
 * Conversation selector: a dropdown listing all conversations for the
 * document alongside a round "new conversation" button (+ icon).
 *
 * Designed to sit inline within a parent flex container — renders only
 * the select and button without its own outer wrapper/border.
 */
export default function ConversationSidebar({
  documentId,
  activeConversationId,
  onSelect,
  onNewConversation,
}: ConversationSidebarProps) {
  const { conversations, isLoading } = useConversations(documentId)

  return (
    <div className="flex items-center gap-2">
      <select
        value={activeConversationId ?? ''}
        onChange={(e) => onSelect(e.target.value)}
        disabled={isLoading || conversations.length === 0}
        className="h-[34px] flex-1 rounded-full border border-slate-300 bg-white pl-3 text-sm text-slate-700 disabled:text-slate-400"
        aria-label="Select conversation"
      >
        {isLoading && <option value="">Loading…</option>}
        {conversations.map((conv) => (
          <option key={conv.id} value={conv.id}>
            {conv.title ?? 'New conversation'}
          </option>
        ))}
      </select>

      <button
        onClick={onNewConversation}
        className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-full border border-slate-300 bg-white text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
        title="New conversation"
        aria-label="New conversation"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
        </svg>
      </button>
    </div>
  )
}
