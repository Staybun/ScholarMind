export interface StreamMessage {
  type: 'content' | 'error' | 'done'
  data?: string
}

export async function consumeSse(
  url: string,
  body: unknown,
  onMessage: (message: StreamMessage) => void,
  signal?: AbortSignal,
) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok || !response.body) throw new Error(`流式请求失败（HTTP ${response.status}）`)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      const dataLines = block.split(/\r?\n/).filter(line => line.startsWith('data:'))
      if (!dataLines.length) continue
      const raw = dataLines.map(line => line.slice(5).trimStart()).join('\n')
      try {
        const message = JSON.parse(raw) as StreamMessage
        onMessage(message)
      } catch {
        onMessage({ type: 'content', data: raw })
      }
    }
    if (done) break
  }
}
