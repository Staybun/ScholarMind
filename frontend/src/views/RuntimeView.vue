<script setup lang="ts">
import { ref } from 'vue'
import { Cpu, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { createRun, getCheckpoints, getRun, getTraces, resumeRun } from '@/api/runtime'
import type { AgentCheckpoint, AgentRun, AgentTrace, ExecutionType } from '@/types'

const executionType = ref<ExecutionType>('REACT'); const input = ref(''); const sessionId = ref(crypto.randomUUID())
const memoryScope = ref('runtime-console'); const runId = ref(''); const run = ref<AgentRun>()
const checkpoints = ref<AgentCheckpoint[]>([]); const traces = ref<AgentTrace[]>([]); const busy = ref(false)
const statusType: Record<string, 'success' | 'warning' | 'danger' | 'info'> = { COMPLETED:'success', RUNNING:'warning', RETRYING:'warning', FAILED:'danger', PENDING:'info' }
async function loadDetails(id = runId.value.trim()) {
  if (!id) return; busy.value = true
  try {
    const [runData, checkpointData, traceData] = await Promise.all([getRun(id), getCheckpoints(id), getTraces(id)])
    run.value = runData; runId.value = id; checkpoints.value = checkpointData ?? []; traces.value = traceData ?? []
  } catch (error) { ElMessage.error((error as Error).message) }
  finally { busy.value = false }
}
async function startRun() {
  if (!input.value.trim()) return; busy.value = true
  try {
    const data = await createRun({ executionType:executionType.value, input:input.value.trim(), sessionId:sessionId.value, memoryScope:memoryScope.value.trim() || 'runtime-console' })
    run.value = data; runId.value = data.runId; await loadDetails(data.runId)
  } catch (error) { ElMessage.error((error as Error).message) }
  finally { busy.value = false }
}
async function resume() {
  if (!runId.value) return; busy.value = true
  try { await resumeRun(runId.value); await loadDetails(runId.value) }
  catch (error) { ElMessage.error((error as Error).message) }
  finally { busy.value = false }
}
const time = (value?: string) => value ? new Date(value).toLocaleString('zh-CN',{hour12:false}) : '—'
</script>

<template>
  <div class="view-stack">
    <header class="page-header"><div><span class="eyebrow">Agent harness / 04</span><h1>Agent 运行台</h1><p>创建、检查和恢复 Agent Run，并追踪 Checkpoint 与执行事件。</p></div><div class="run-lookup"><el-input v-model="runId" placeholder="输入 Run ID" @keyup.enter="loadDetails()" /><el-button :icon="Search" :loading="busy" @click="loadDetails()">查询</el-button></div></header>
    <section class="runtime-grid">
      <aside class="panel run-form">
        <span class="eyebrow">New execution</span><h2>启动任务</h2>
        <label>执行模式</label><el-segmented v-model="executionType" :options="['REACT','PLAN_EXECUTE','MULTI_AGENT']" />
        <label>任务输入</label><el-input v-model="input" type="textarea" :autosize="{minRows:7,maxRows:12}" resize="none" placeholder="描述 Agent 需要执行的任务…" />
        <label>记忆域</label><el-input v-model="memoryScope" />
        <el-button class="launch-button" type="primary" :icon="Cpu" :loading="busy" :disabled="!input.trim()" @click="startRun">创建 Agent Run</el-button>
      </aside>
      <div class="runtime-main">
        <EmptyState v-if="!run" :icon="Cpu" title="尚未选择运行记录" description="创建新任务或输入已有 Run ID，以检查生命周期状态与执行轨迹。" />
        <template v-else>
          <section class="panel run-summary">
            <div class="summary-top"><div><span class="eyebrow">{{ run.executionType }}</span><h2>{{ run.currentStep || 'Agent Run' }}</h2></div><el-tag :type="statusType[run.status] || 'info'" effect="dark">{{ run.status }}</el-tag></div>
            <div class="run-id">{{ run.runId }}</div>
            <div class="metric-grid"><div><span>ATTEMPTS</span><strong>{{ run.attemptCount }}</strong></div><div><span>CREATED</span><strong>{{ time(run.createdAt) }}</strong></div><div><span>UPDATED</span><strong>{{ time(run.updatedAt) }}</strong></div></div>
            <div v-if="run.output || run.errorMessage" class="run-output" :class="{error:run.errorMessage}">{{ run.errorMessage || run.output }}</div>
            <div class="summary-actions"><el-button :icon="RefreshRight" :loading="busy" @click="loadDetails()">刷新</el-button><el-button type="primary" :disabled="run.status==='COMPLETED'" :loading="busy" @click="resume">恢复运行</el-button></div>
          </section>
          <section class="inspect-grid">
            <div class="panel inspect-panel"><div class="inspect-head"><h3>Checkpoints</h3><span>{{ checkpoints.length }}</span></div><div v-if="!checkpoints.length" class="muted-row">暂无检查点</div><div v-for="item in checkpoints" :key="item.id" class="timeline-row"><i/><div><strong>{{ item.stepName }}</strong><p>{{ item.payload || '状态已持久化' }}</p><small>{{ time(item.createdAt) }}</small></div></div></div>
            <div class="panel inspect-panel"><div class="inspect-head"><h3>Trace events</h3><span>{{ traces.length }}</span></div><div v-if="!traces.length" class="muted-row">暂无追踪事件</div><div v-for="item in traces" :key="item.id" class="trace-row"><div><strong>{{ item.eventType }}</strong><span>{{ item.stepName }}</span></div><p>{{ item.detail || '—' }}</p><small>{{ time(item.createdAt) }}</small></div></div>
          </section>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.run-lookup{display:flex;gap:8px;width:min(420px,100%)}.runtime-grid{display:grid;grid-template-columns:330px minmax(0,1fr);gap:18px;align-items:start}.run-form{padding:26px}.run-form h2{margin:8px 0 26px;font:600 30px/1.1 Georgia,serif}.run-form label{display:block;margin:18px 0 8px;color:var(--muted);font-size:11px;font-weight:800;letter-spacing:.1em}.launch-button{width:100%;margin-top:22px}.runtime-main{display:grid;gap:18px}.run-summary{padding:28px}.summary-top{display:flex;justify-content:space-between;align-items:flex-start}.summary-top h2{margin:6px 0 0;font:600 30px/1.1 Georgia,serif}.run-id{margin:22px 0;padding:10px 12px;color:var(--muted);background:var(--paper-deep);font:12px/1.4 monospace;word-break:break-all}.metric-grid{display:grid;grid-template-columns:.6fr 1fr 1fr;border:1px solid var(--line)}.metric-grid div{padding:15px;border-right:1px solid var(--line)}.metric-grid div:last-child{border-right:0}.metric-grid span{display:block;color:var(--muted);font-size:9px;letter-spacing:.12em}.metric-grid strong{display:block;margin-top:6px;font-size:13px}.run-output{margin-top:18px;padding:16px;border-left:3px solid #6e9c37;background:rgba(203,243,108,.15);white-space:pre-wrap}.run-output.error{border-color:#c64739;background:#fff0ed}.summary-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:20px}.inspect-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.inspect-panel{padding:22px}.inspect-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.inspect-head h3{margin:0;font:600 21px/1 Georgia,serif}.inspect-head span{color:var(--orange);font-weight:800}.timeline-row{display:grid;grid-template-columns:18px 1fr;padding-bottom:18px}.timeline-row i{width:8px;height:8px;margin-top:5px;border-radius:50%;background:var(--orange);box-shadow:0 0 0 4px #fff1e8}.timeline-row p,.trace-row p{margin:6px 0;color:var(--muted);font-size:12px;white-space:pre-wrap;word-break:break-word}.timeline-row small,.trace-row small{color:#aaa79c}.trace-row{padding:14px 0;border-top:1px solid var(--line)}.trace-row>div{display:flex;justify-content:space-between;gap:12px}.trace-row span{color:var(--muted);font-size:11px}.muted-row{padding:24px 0;color:var(--muted);text-align:center}@media(max-width:980px){.runtime-grid{grid-template-columns:1fr}}@media(max-width:700px){.inspect-grid{grid-template-columns:1fr}.metric-grid{grid-template-columns:1fr}.metric-grid div{border-right:0;border-bottom:1px solid var(--line)}}
</style>
