interface ChatPaneProps {
  open: boolean
  onClose: () => void
}

/**
 * Placeholder slide-in chat panel for Phase 2 RAG chat.
 *
 * Uses CSS translate + transition for the slide animation rather than
 * mounting/unmounting — the panel is always in the DOM but off-screen
 * when closed. This avoids re-mount flicker and lets CSS handle the
 * animation smoothly.
 */
export default function ChatPane({ open, onClose }: ChatPaneProps) {
  return (
    <>
      {/* Semi-transparent backdrop */}
      <div
        className={`fixed inset-0 z-40 bg-black/30 transition-opacity ${
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
        onClick={onClose}
      />

      {/* Slide-in panel */}
      <div
        className={`fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col bg-white shadow-xl transition-transform duration-300 ${
          open ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h3 className="text-lg font-semibold text-slate-900">Ask a Question</h3>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Messages area — placeholder */}
        <div className="flex flex-1 items-center justify-center px-5">
          <p className="text-centre text-sm text-slate-400">
            Chat is coming soon. You'll be able to ask questions about this document here.
          </p>
        </div>

        {/* Disabled input */}
        <div className="border-t border-slate-200 px-5 py-4">
          <div className="flex gap-2">
            <input
              type="text"
              disabled
              placeholder="Type a question…"
              className="flex-1 rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-400"
            />
            <button
              disabled
              className="rounded-md bg-indigo-400 px-4 py-2 text-sm font-medium text-white"
            >
              Send
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
