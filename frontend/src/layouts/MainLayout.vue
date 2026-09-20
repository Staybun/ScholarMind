<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Collection, DataAnalysis, Operation, Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const compact = ref(false)
const navigation = [
  { to: '/chat', label: '论文问答', note: 'Evidence chat', icon: ChatDotRound, index: '01' },
  { to: '/papers', label: '论文知识库', note: 'Hybrid index', icon: Collection, index: '02' },
  { to: '/research', label: '研究工作流', note: 'Multi-agent', icon: DataAnalysis, index: '03' },
  { to: '/runtime', label: 'Agent Runtime', note: 'Runs & traces', icon: Operation, index: '04' },
]
const pageTitle = computed(() => route.meta.title as string || 'ScholarMind')
const eyebrow = computed(() => route.meta.eyebrow as string || 'LAB')
</script>

<template>
  <div class="shell" :class="{ compact }">
    <aside class="rail">
      <div class="brand-mark">
        <div class="brand-glyph">S<span>M</span></div>
        <div class="brand-copy">
          <strong>ScholarMind</strong>
          <small>RESEARCH AGENT / 2026</small>
        </div>
      </div>

      <nav class="navigation" aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" class="nav-item">
          <span class="nav-index">{{ item.index }}</span>
          <el-icon :size="21"><component :is="item.icon" /></el-icon>
          <span class="nav-copy"><b>{{ item.label }}</b><small>{{ item.note }}</small></span>
        </RouterLink>
      </nav>

      <div class="rail-foot">
        <div class="system-pulse"><i /> SYSTEM READY</div>
        <button class="rail-toggle" type="button" @click="compact = !compact" :aria-label="compact ? '展开侧栏' : '收起侧栏'">
          <el-icon><component :is="compact ? Expand : Fold" /></el-icon>
        </button>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <span class="page-eyebrow">{{ eyebrow }} / SCHOLARMIND</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-meta">
          <span>RAG · REACT · MULTI-AGENT</span>
          <b>{{ new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) }}</b>
        </div>
      </header>
      <div class="page-stage"><RouterView /></div>
    </main>
  </div>
</template>
