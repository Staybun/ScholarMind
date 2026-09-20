<script setup lang="ts">
import { computed, ref } from 'vue'
import { MagicStick, VideoPause } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownBody from '@/components/MarkdownBody.vue'
import EmptyState from '@/components/EmptyState.vue'
import { streamResearch } from '@/api/chat'

const task = ref(''); const report = ref(''); const memoryScope = ref('research-lab')
const sessionId = ref(crypto.randomUUID()); const running = ref(false); const controller = ref<AbortController>()
const stage = computed(() => !running.value && !report.value ? 0 : running.value && report.value.length < 300 ? 1 : running.value ? 2 : 3)
async function runWorkflow() {
  const input = task.value.trim(); if (!input || running.value) return
  report.value = ''; running.value = true; controller.value = new AbortController()
  try { await streamResearch({ SessionId: sessionId.value, MemoryScope: memoryScope.value.trim() || 'research-lab', Task: input }, chunk => { report.value += chunk }, controller.value.signal) }
  catch (error) { if ((error as Error).name !== 'AbortError') ElMessage.error((error as Error).message || '研究工作流执行失败') }
  finally { running.value = false; controller.value = undefined }
}
function stopWorkflow() { controller.value?.abort(); running.value = false }
</script>

<template>
  <div class="view-stack">
    <header class="page-header research-header"><div><span class="eyebrow">Plan & execute / 03</span><h1>深度研究工作台</h1><p>提交完整研究目标，由多 Agent 拆解、检索、分析并组织最终报告。</p></div><el-input v-model="memoryScope" class="scope-input" placeholder="记忆域" /></header>
    <section class="research-grid">
      <aside class="panel brief-panel">
        <span class="block-label">RESEARCH BRIEF</span><h2>定义研究任务</h2>
        <el-input v-model="task" type="textarea" :autosize="{ minRows: 9, maxRows: 16 }" resize="none" placeholder="例如：调研 RAG 系统中混合检索与精排的常见方案，比较性能、成本与适用场景，并给出可复现实验设计。" />
        <div class="brief-actions"><el-button v-if="running" :icon="VideoPause" @click="stopWorkflow">停止</el-button><el-button type="primary" :icon="MagicStick" :loading="running" :disabled="!task.trim()" @click="runWorkflow">{{ running ? 'Agent 执行中' : '启动研究' }}</el-button></div>
        <div class="stage-list"><div v-for="(label,index) in ['规划任务','检索与分析','综合撰写']" :key="label" class="stage" :class="{active:stage>=index+1}"><span>{{ String(index+1).padStart(2,'0') }}</span><p>{{ label }}</p></div></div>
      </aside>
      <article class="panel report-panel">
        <div class="report-head"><div><span class="eyebrow">Live synthesis</span><h2>研究报告</h2></div><span v-if="running" class="live-indicator"><i /> LIVE</span></div>
        <EmptyState v-if="!report" :icon="MagicStick" title="报告将在这里生成" description="写清研究范围、期望输出和约束条件，可以得到更稳定的执行计划。" />
        <MarkdownBody v-else class="report-content" :content="report" />
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
.research-grid{display:grid;grid-template-columns:360px minmax(0,1fr);gap:18px;align-items:start}.brief-panel{position:sticky;top:28px;padding:26px}.block-label{color:var(--orange);font-size:11px;font-weight:800;letter-spacing:.15em}.brief-panel h2,.report-head h2{margin:8px 0 20px;font:600 30px/1.1 Georgia,serif}.brief-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}.stage-list{margin-top:30px;padding-top:22px;border-top:1px solid var(--line)}.stage{display:grid;grid-template-columns:34px 1fr;align-items:center;color:#a5a39a}.stage span{font:700 12px/1 Georgia,serif}.stage p{margin:0;padding:13px 0;border-bottom:1px solid var(--line)}.stage.active{color:var(--ink)}.stage.active span{color:var(--orange)}.report-panel{min-height:calc(100vh - 220px);padding:32px 38px}.report-head{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:20px;border-bottom:1px solid var(--line)}.report-head h2{margin-bottom:0}.live-indicator{color:#9d3c19;font-size:10px;font-weight:800;letter-spacing:.14em}.live-indicator i{display:inline-block;width:7px;height:7px;margin-right:5px;border-radius:50%;background:var(--orange);animation:pulse 1.3s infinite}.report-content{margin-top:28px}.scope-input{width:180px}@keyframes pulse{50%{opacity:.28;transform:scale(.75)}}@media(max-width:900px){.research-grid{grid-template-columns:1fr}.brief-panel{position:static}.report-panel{min-height:520px}}
</style>
