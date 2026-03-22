import type { ReactNode } from 'react'

interface FormattedContentProps {
  text: string
}

/**
 * Renders chat message text with lightweight markdown formatting.
 *
 * Supports the subset of markdown that the AI typically uses in responses:
 * - **bold** (double asterisks)
 * - *italic* (single asterisks)
 * - `code` (backticks)
 * - Line breaks (preserved via block-level splitting)
 * - Lines starting with "- " rendered as list items
 *
 * This avoids pulling in a full markdown library (react-markdown + remark)
 * for what amounts to inline formatting in short chat responses.
 */
export default function FormattedContent({ text }: FormattedContentProps) {
  const lines = text.split('\n')

  return (
    <div className="space-y-1">
      {lines.map((line, i) => {
        const trimmed = line.trimStart()

        // List items: lines starting with "- " (possibly indented)
        if (trimmed.startsWith('- ')) {
          // Count leading spaces to determine nesting depth.
          // Each 2 spaces of indentation = one nesting level.
          const indent = line.length - line.trimStart().length
          const depth = Math.floor(indent / 2)

          return (
            <div key={i} className="flex gap-2" style={{ paddingLeft: `${depth * 16 + 4}px` }}>
              <span className="shrink-0 text-slate-400" style={{ fontSize: '1.25rem', lineHeight: '1.25rem' }}>{depth > 0 ? '◦' : '•'}</span>
              <span>{parseInlineFormatting(trimmed.slice(2))}</span>
            </div>
          )
        }

        // Empty lines become spacing
        if (trimmed === '') {
          return <div key={i} className="h-2" />
        }

        // Regular paragraph
        return <p key={i}>{parseInlineFormatting(line)}</p>
      })}
    </div>
  )
}

/**
 * Parses inline markdown formatting into React elements.
 *
 * Processes in order of specificity: backtick code first (so asterisks
 * inside code spans aren't interpreted), then bold (**), then italic (*).
 * Each pass splits on the delimiter, wraps alternating segments in the
 * appropriate element, and recurses for nested formatting.
 */
function parseInlineFormatting(text: string): ReactNode {
  return parseCode(text)
}

/** Splits on backtick-delimited code spans: `code` */
function parseCode(text: string): ReactNode {
  const parts = text.split(/(`[^`]+`)/)
  if (parts.length === 1) return parseBold(text)

  return parts.map((part, i) => {
    if (part.startsWith('`') && part.endsWith('`')) {
      return (
        <code key={i} className="rounded bg-slate-100 px-1 py-0.5 font-mono text-xs text-slate-800">
          {part.slice(1, -1)}
        </code>
      )
    }
    return <span key={i}>{parseBold(part)}</span>
  })
}

/** Splits on double-asterisk bold: **bold** */
function parseBold(text: string): ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*)/)
  if (parts.length === 1) return parseItalic(text)

  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={i} className="font-semibold">{parseItalic(part.slice(2, -2))}</strong>
    }
    return <span key={i}>{parseItalic(part)}</span>
  })
}

/** Splits on single-asterisk italic: *italic* */
function parseItalic(text: string): ReactNode {
  // Use a stricter regex to avoid matching bare asterisks in expressions like "2 * 3"
  // Only match *word...* where the content doesn't start/end with a space
  const parts = text.split(/(\*[^\s*][^*]*[^\s*]\*|\*[^\s*]\*)/)
  if (parts.length === 1) return text

  return parts.map((part, i) => {
    if (part.startsWith('*') && part.endsWith('*') && !part.startsWith('**')) {
      return <em key={i}>{part.slice(1, -1)}</em>
    }
    return <span key={i}>{part}</span>
  })
}
