<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Connection, DocumentAdd, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { checkMilvus, searchPapers, uploadPaper } from '@/api/papers'
import type { PaperSearchResult } from '@/types'

const query = ref(''); const topK = ref(10); const results = ref<PaperSearchResult[]>([])
const searching = ref(false); const uploading = ref(false); const health = ref<'checking' | 'up' | 'down'>('checking')
const uploadInput = ref<HTMLInputElement>()
async function refreshHealth() {
  health.value = 'checking'
  try { await checkMilvus(); health.value = 'up' } catch { health.value = 'down' }
}
async function search() {
  if (!query.value.trim()) return
  searching.value = true
  try { results.value = await searchPapers(query.value.trim(), topK.value) ?? [] }
  catch (error) { ElMessage.error((error as Error).message) }
  finally { searching.value = false }
}
async function handleFile(event: Event) {
  const element = event.target as HTMLInputElement; const file = element.files?.[0]
  if (!file) return
  uploading.value = true
  try { await uploadPaper(file); ElMessage.success('论文已解析并写入知识库'); await refreshHealth() }
  catch (error) { ElMessage.error((error as Error).message) }
  finally { uploading.value = false; element.value = '' }
}
const score = (value?: number) => typeof value === 'number' ? value.toFixed(4) : '—'
const source = (item: PaperSearchResult) => item.documentTitle || item.sourceFileName || 'UNKNOWN SOURCE'
onMounted(refreshHealth)
</script>

<template>
  <div class="view-stack">
    <header class="page-header">
      <div><span class="eyebrow">Hybrid retrieval / 02</span><h1>论文知识库</h1><p>上传论文并用向量、BM25、RRF 融合与 BGE 精排检索关键段落。</p></div>
      <div class="health-pill" :class="`is-${health}`"><span class="health-dot" />Milvus {{ health === 'up' ? '已连接' : health === 'down' ? '未连接' : '检测中' }}</div>
    </header>
    <section class="paper-toolbar">
      <div class="panel search-card">
        <span class="block-label">01 / RETRIEVE</span>
        <div class="search-row"><el-input v-model="query" size="large" clearable placeholder="输入研究概念、方法或结论" @keyup.enter="search" /><el-input-number v-model="topK" :min="1" :max="50" controls-position="right" /><el-button type="primary" size="large" :icon="Search" :loading="searching" @click="search">混合检索</el-button></div>
        <div class="pipeline-note"><span>Dense</span><b>+</b><span>BM25</span><b>→</b><span>RRF</span><b>→</b><span>BGE-Reranker-v2-m3</span></div>
      </div>
      <div class="panel upload-card" @click="uploadInput?.click()"><input ref="uploadInput" hidden type="file" accept=".pdf,.doc,.docx,.txt,.md" @change="handleFile" /><el-icon :size="26"><DocumentAdd /></el-icon><strong>{{ uploading ? '正在处理论文…' : '添加论文文档' }}</strong><span>PDF / Word / TXT / Markdown</span></div>
    </section>
    <section class="results-section">
      <div class="section-heading"><div><span class="eyebrow">Evidence fragments</span><h2>检索结果</h2></div><span class="result-count">{{ results.length.toString().padStart(2, '0') }} RESULTS</span></div>
      <EmptyState v-if="results.length === 0" :icon="Connection" title="等待检索" description="结果会展示来源、融合排名和精排得分，便于检查召回质量。" />
      <div v-else class="result-list">
        <article v-for="(item,index) in results" :key="`${item.id}-${index}`" class="result-card panel">
          <div class="result-index">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="result-main"><div class="result-source">{{ source(item) }}</div><p>{{ item.content }}</p><div class="score-row"><span>Dense <b>{{ score(item.denseScore) }}</b></span><span>BM25 <b>{{ score(item.keywordScore) }}</b></span><span>RRF <b>{{ score(item.fusionScore) }}</b></span><span class="score-highlight">Rerank <b>{{ score(item.rerankScore) }}</b></span></div></div>
          <div class="rank-column"><span>FINAL</span><strong>#{{ index + 1 }}</strong><small>{{ item.rerankStrategy || 'fusion' }}</small></div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.paper-toolbar{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:18px}.search-card{padding:24px}.block-label{display:block;margin-bottom:14px;color:var(--muted);font-size:11px;font-weight:800;letter-spacing:.14em}.search-row{display:grid;grid-template-columns:1fr 128px auto;gap:10px}.pipeline-note{display:flex;flex-wrap:wrap;gap:10px;margin-top:16px;color:var(--muted);font-size:12px}.pipeline-note b{color:var(--orange)}.upload-card{display:flex;flex-direction:column;justify-content:center;align-items:flex-start;gap:8px;padding:24px;color:#fff;background:var(--ink);cursor:pointer;transition:.2s}.upload-card:hover{transform:translateY(-3px);background:var(--ink-soft)}.upload-card span{color:rgba(255,255,255,.62);font-size:12px}.results-section{margin-top:36px}.section-heading{display:flex;justify-content:space-between;align-items:end;margin-bottom:16px}.section-heading h2{margin:4px 0 0;font:600 30px/1.1 Georgia,serif}.result-count{color:var(--muted);font-size:11px;font-weight:800;letter-spacing:.14em}.result-list{display:grid;gap:12px}.result-card{display:grid;grid-template-columns:64px 1fr 92px;overflow:hidden}.result-index{padding:24px 16px;color:var(--orange);font:700 22px/1 Georgia,serif;border-right:1px solid var(--line)}.result-main{padding:22px 26px}.result-source{color:var(--muted);font-size:11px;font-weight:800;letter-spacing:.1em}.result-main p{margin:10px 0 18px;line-height:1.8}.score-row{display:flex;flex-wrap:wrap;gap:8px}.score-row span{padding:5px 8px;background:var(--paper-deep);color:var(--muted);font-size:11px}.score-row b{color:var(--ink)}.score-row .score-highlight{background:#fff1e8;color:#9d3c19}.rank-column{display:flex;flex-direction:column;align-items:center;justify-content:center;border-left:1px solid var(--line)}.rank-column span{color:var(--muted);font-size:9px;letter-spacing:.14em}.rank-column strong{margin:7px 0;font:700 24px/1 Georgia,serif}.rank-column small{max-width:80px;color:var(--muted);text-align:center;word-break:break-word}.health-pill{display:flex;align-items:center;gap:8px;padding:9px 12px;border:1px solid var(--line);font-size:12px}.health-dot{width:7px;height:7px;border-radius:50%;background:#c28a2d}.health-pill.is-up .health-dot{background:#6e9c37;box-shadow:0 0 0 4px rgba(110,156,55,.15)}.health-pill.is-down .health-dot{background:#c64739}@media(max-width:900px){.paper-toolbar{grid-template-columns:1fr}.upload-card{min-height:150px}}@media(max-width:680px){.search-row{grid-template-columns:1fr}.result-card{grid-template-columns:48px 1fr}.rank-column{display:none}.result-main{padding:18px}}
</style>
