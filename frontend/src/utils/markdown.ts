import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { marked } from 'marked'

marked.use({
  gfm: true,
  breaks: true,
})

export function renderMarkdown(content: string) {
  if (!content) return ''
  const html = marked.parse(content, { async: false }) as string
  return DOMPurify.sanitize(html)
}

export function highlightCodeBlocks(root: HTMLElement) {
  root.querySelectorAll('pre code').forEach((block) => {
    if (!block.classList.contains('hljs')) {
      hljs.highlightElement(block as HTMLElement)
    }
  })
}

export function escapeHtml(text: string) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}
