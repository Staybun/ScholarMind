import { http } from './http'
import type { AgentCheckpoint, AgentRun, AgentTrace, ExecutionType } from '../types'

export const createRun = async (input: { executionType: ExecutionType; input: string; sessionId: string; memoryScope: string }) =>
  (await http.post<AgentRun>('/agent-runs', input, { timeout: 600_000 })).data

export const getRun = async (runId: string) => (await http.get<AgentRun>(`/agent-runs/${runId}`)).data
export const resumeRun = async (runId: string) => (await http.post<AgentRun>(`/agent-runs/${runId}/resume`, {}, { timeout: 600_000 })).data
export const getCheckpoints = async (runId: string) => (await http.get<AgentCheckpoint[]>(`/agent-runs/${runId}/checkpoints`)).data
export const getTraces = async (runId: string) => (await http.get<AgentTrace[]>(`/agent-runs/${runId}/traces`)).data
