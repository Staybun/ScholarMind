<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ChatDotRound, Delete, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownBody from '@/components/MarkdownBody.vue'
import EmptyState from '@/components/EmptyState.vue'
import { streamChat } from '@/api/chat'

interface ChatMessage { id: string; role: 'user' | 'assistant'; content: string }
const messages = ref<ChatMessage[]>([])
const question = ref('')
const memoryScope = ref('scholar-session')
const sessionId = ref(crypto.randomUUID())
const loading = ref(false)
const controller = ref<AbortController>()
const messageList = ref<HTMLElement>()
const canSend = computed(() => question.value.trim().length > 0 && !loading.value)

function scrollToLatest() {
  void nextTick(() => messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' }))
}
function resetConversation() {
  controller.value?.abort(); messages.value = []; question.value = ''; sessionId.value = crypto.randomUUID(); loading.value = false
}
async function sendQuestion() {
  const text = question.value.trim()
  if (!text || loading.value) return
  question.value = ''
  const answer: ChatMessage = { id: crypto.randomUUID(), role: 'assistant', content: '' }
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: text }, answer)
  loading.value = true; controller.value = new AbortController(); scrollToLatest()
  try {
    await streamChat({ Id: sessionId.value, Question: text, MemoryScope: memoryScope.value.trim() || 'scholar-session' }, chunk => {
      answer.content += chunk; scrollToLatest()
    }, controller.value.signal)
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      ElMessage.error((error as Error).message || '问答请求失败')
      if (!answer.content) answer.content = '请求未完成，请检查服务状态后重试。'
    }
  } finally { loading.value = false; controller.value = undefined; scrollToLatest() }
}
function handleKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') void sendQuestion()
}
</script>

<template>
  <div class="view-stack">
    <header class="page-header">
      <div><span class="eyebrow">Knowledge dialogue / 01</span><h1>论文知识问答</h1><p>围绕已入库论文展开连续追问，回答会保留当前会话中的研究语境。</p></div>
      <div class="header-actions"><el-input v-model="memoryScope" class="scope-input" placeholder="记忆域" /><el-button :icon="Delete" @click="resetConversation">新会话</el-button></div>
    </header>
    <section class="panel chat-panel">
      <div ref="messageList" class="message-list">
        <EmptyState v-if="messages.length === 0" :icon="ChatDotRound" title="从一个研究问题开始" description="要求系统比较论文、解释方法，或基于知识库梳理一条证据链。">
          <div class="prompt-grid">
            <button @click="question = '总结知识库中关于多智能体协作的主要研究路线'">总结一个研究方向</button>
            <button @click="question = '比较检索增强生成与传统问答系统的差异'">比较两类方法</button>
            <button @click="question = '给出一个可复现的实验验证步骤清单'">规划复现实验</button>
          </div>
        </EmptyState>
        <article v-for="message in messages" :key="message.id" class="message" :class="`is-${message.role}`">
          <div class="message-meta">{{ message.role === 'user' ? 'YOU' : 'SCHOLARMIND' }}</div>
          <div class="message-body"><MarkdownBody v-if="message.role === 'assistant'" :content="message.content || '正在组织回答…'" /><p v-else>{{ message.content }}</p></div>
        </article>
      </div>
      <div class="composer">
        <el-input v-model="question" type="textarea" :autosize="{ minRows: 3, maxRows: 7 }" resize="none" placeholder="输入与论文、方法或实验相关的问题…" @keydown="handleKeydown" />
        <div class="composer-foot"><span>Ctrl / ⌘ + Enter 发送</span><el-button type="primary" :icon="Promotion" :loading="loading" :disabled="!canSend" @click="sendQuestion">{{ loading ? '生成中' : '发送问题' }}</el-button></div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.chat-panel{min-height:calc(100vh - 220px);display:grid;grid-template-rows:1fr auto;overflow:hidden}.message-list{overflow-y:auto;padding:34px;min-height:420px;max-height:calc(100vh - 390px)}.message{max-width:780px;margin:0 0 28px}.message.is-user{margin-left:auto;width:min(78%,680px)}.message-meta{margin-bottom:8px;color:var(--muted);font-size:11px;font-weight:700;letter-spacing:.14em}.is-user .message-meta{text-align:right}.message-body{padding:20px 22px;border:1px solid var(--line);background:#fffef9}.is-user .message-body{background:var(--ink);color:#fff;border-color:var(--ink)}.message-body p{margin:0;white-space:pre-wrap}.composer{border-top:1px solid var(--line);padding:20px 24px;background:rgba(255,254,249,.88)}.composer-foot{display:flex;justify-content:space-between;align-items:center;margin-top:12px;color:var(--muted);font-size:12px}.prompt-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;width:min(720px,100%)}.prompt-grid button{padding:14px;border:1px solid var(--line);background:transparent;color:var(--ink);text-align:left;cursor:pointer;transition:.2s}.prompt-grid button:hover{border-color:var(--orange);background:#fff1e8;transform:translateY(-2px)}.scope-input{width:180px}@media(max-width:760px){.message-list{padding:20px;max-height:none}.prompt-grid{grid-template-columns:1fr}.message.is-user{width:90%}.header-actions{width:100%}.scope-input{flex:1}}
</style>
