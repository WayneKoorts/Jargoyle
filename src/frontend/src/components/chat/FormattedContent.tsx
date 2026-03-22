import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface FormattedContentProps {
  text: string
}

/**
 * Renders chat message text with Markdown formatting.
 *
 * Uses react-markdown with remark-gfm (GitHub Flavoured Markdown) to support
 * the full range of formatting the AI produces: headings, bold, italic,
 * strikethrough, inline code, fenced code blocks, ordered and unordered lists,
 * tables, blockquotes, horizontal rules, and links.
 *
 * Custom component overrides apply Tailwind styling appropriate for chat
 * bubbles (scaled-down headings, horizontally scrollable tables and code
 * blocks, etc.).
 */
export default function FormattedContent({ text }: FormattedContentProps) {
  return (
    <div className="space-y-1">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
        {text}
      </ReactMarkdown>
    </div>
  )
}

/**
 * Custom component overrides for react-markdown.
 *
 * Applies Tailwind CSS classes appropriate for rendering inside a chat bubble.
 * Headings are scaled down, code blocks use a dark theme, and tables are
 * wrapped in a horizontally scrollable container.
 *
 * For code elements, inline code gets a light pill-style background. Fenced
 * code blocks are wrapped in a {@code <pre>} that resets the inline styling
 * via Tailwind's child selector variant {@code [&>code]}.
 */
const markdownComponents: Components = {
  // --- Headings (scaled for chat bubbles) ---
  h1: ({ children }) => <h1 className="text-base font-bold mt-3 mb-1">{children}</h1>,
  h2: ({ children }) => <h2 className="text-base font-semibold mt-3 mb-1">{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-semibold mt-2 mb-0.5">{children}</h3>,
  h4: ({ children }) => <h4 className="text-sm font-semibold mt-2 mb-0.5">{children}</h4>,
  h5: ({ children }) => <h5 className="text-sm font-semibold mt-1.5 mb-0.5">{children}</h5>,
  h6: ({ children }) => <h6 className="text-sm font-semibold mt-1.5 mb-0.5">{children}</h6>,

  // --- Block elements ---
  p: ({ children }) => <p className="my-1">{children}</p>,
  blockquote: ({ children }) => (
    <blockquote className="border-l-3 border-slate-300 pl-3 italic text-slate-600">
      {children}
    </blockquote>
  ),
  hr: () => <hr className="border-t border-slate-200 my-2" />,

  // --- Lists ---
  ul: ({ children }) => <ul className="list-disc pl-5 space-y-0.5">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal pl-5 space-y-0.5">{children}</ol>,

  // --- Code ---
  // Fenced code blocks render as <pre><code>...</code></pre>.
  // The [&>code] variants reset the inline code styling so the dark
  // pre background shows through instead.
  pre: ({ children }) => (
    <pre className="my-2 rounded-lg bg-slate-800 text-slate-100 p-3 font-mono text-xs overflow-x-auto [&>code]:bg-transparent [&>code]:p-0 [&>code]:text-inherit [&>code]:rounded-none">
      {children}
    </pre>
  ),
  code: ({ children }) => (
    <code className="rounded bg-slate-100 px-1 py-0.5 font-mono text-xs text-slate-800">
      {children}
    </code>
  ),

  // --- Inline formatting ---
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  del: ({ children }) => <del className="line-through">{children}</del>,

  // --- Links (open in new tab for safety) ---
  a: ({ children, href }) => (
    <a
      href={href}
      className="text-indigo-600 underline hover:text-indigo-800"
      target="_blank"
      rel="noopener noreferrer"
    >
      {children}
    </a>
  ),

  // --- Tables (horizontally scrollable wrapper) ---
  table: ({ children }) => (
    <div className="my-2 overflow-x-auto">
      <table className="min-w-full text-xs">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border-b border-slate-300 px-2 py-1 text-left font-semibold whitespace-nowrap">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="border-b border-slate-200 px-2 py-1">{children}</td>
  ),
}
