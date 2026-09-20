export interface ApiResponse<T> { code: number; message: string; data: T }

export interface PaperSearchResult {
  id: string
  content: string
  score: number
  documentTitle?: string
  sourceFileName?: string
  sectionTitle?: string
  pageStart?: number
  pageEnd?: number
  chunkIndex?: number
  denseRank?: number
  keywordRank?: number
  denseScore?: number
  keywordScore?: number
  fusionScore?: number
  rerankScore?: number
  rerankStrategy?: 'BGE_RERANKER_V2_M3' | 'LOCAL_LEXICAL_FALLBACK'
  retrievalSources?: string[]
}

export type ExecutionType = 'REACT' | 'PLAN_EXECUTE' | 'MULTI_AGENT'
export type RunStatus = 'PENDING' | 'RUNNING' | 'RETRYING' | 'COMPLETED' | 'FAILED'

export interface AgentRun {
  runId: string
  sessionId: string
  memoryScope: string
  executionType: ExecutionType
  status: RunStatus
  output?: string
  errorMessage?: string
  currentStep?: string
  attemptCount: number
  createdAt: string
  updatedAt: string
}

export interface AgentCheckpoint {
  id: number
  runId: string
  sequenceNumber: number
  stepName: string
  status: string
  payload: string
  createdAt: string
}

export interface AgentTrace {
  id: number
  runId: string
  eventType: string
  stepName?: string
  attempt?: number
  durationMs?: number
  detail?: string
  createdAt: string
}
