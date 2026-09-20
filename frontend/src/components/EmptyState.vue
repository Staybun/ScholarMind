<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{ index?: string; icon?: Component; title: string; description: string }>()
</script>

<template>
  <div class="empty-state">
    <el-icon v-if="icon" :size="30"><component :is="icon" /></el-icon>
    <span v-else>{{ index || 'SCHOLARMIND' }}</span>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>
