import { postSseStream } from './stream'
import type { StreamCallbacks } from './stream'

export function startAIOpsStream(callbacks: StreamCallbacks) {
  return postSseStream('/ai_ops', undefined, callbacks)
}
