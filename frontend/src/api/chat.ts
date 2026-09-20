import { consumeSse } from './stream'

export interface ChatStreamInput {
  Id: string
  Question: string
  MemoryScope: string
}

export const streamChat = (input: ChatStreamInput, onContent: (content: string) => void, signal?: AbortSignal) =>
  consumeSse('/api/chat_stream', input, message => {
    if (message.type === 'error') throw new Error(message.data || '问答失败')
    if (message.type === 'content') onContent(message.data || '')
  }, signal)

export const streamResearch = (
  input: { SessionId: string; MemoryScope: string; Task: string },
  onContent: (content: string) => void,
  signal?: AbortSignal,
) => consumeSse('/api/research/workflow', input, message => {
  if (message.type === 'error') throw new Error(message.data || '研究工作流失败')
  if (message.type === 'content') onContent(message.data || '')
}, signal)
