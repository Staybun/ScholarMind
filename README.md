# ScholarMind — 学术论文 Agent 系统

ScholarMind 面向科研论文研读与实验复现规划，提供论文知识问答、研究任务编排、工具调用和分层上下文记忆。

## 功能与代码

| 功能 | 实现位置与范围 |
| --- | --- |
| Agent Runtime Harness | `runtime/`：Run 状态、持久化 Checkpoint、Resume、Retry、Trace |
| 论文知识库 | `service/`：PDF、Markdown、TXT 解析，分片、向量化、BM25 + Milvus 混合检索、重排序与 RAG |
| 单 Agent 与多 Agent | ReAct、Plan-Execute 执行器；Supervisor 协调论文检索、研究问答、实验规划 Agent |
| MCP 与 Skills | 文件系统、GitHub、arXiv MCP 配置；论文阅读与实验复现 Skills |
| 上下文与记忆 | `context/`、`memory/`：Token Budget、滑动窗口、历史摘要、Redis 工作记忆、持久化会话及语义记忆 |


## Docker 部署

安装 Docker Engine 或 Docker Desktop（Linux 容器模式）及 Docker Compose v2。无需在宿主机安装 Java、Maven、Milvus 或 Redis；首次构建需要联网下载镜像和 Maven 依赖。

1. 在项目根目录复制 `.env.example` 为 `.env`（PowerShell：`Copy-Item .env.example .env`；Linux/macOS：`cp .env.example .env`）。
2. 编辑 `.env`，填写 `DASHSCOPE_API_KEY`。该文件已被 Git 和镜像构建上下文排除，请勿提交真实密钥。缺少或留空密钥时 Compose 会直接报错。
3. 在项目根目录运行：

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

启动后打开 <http://localhost:9900>。Compose 启动 `app`、`redis`、`milvus`、`etcd` 和 `minio`；应用等待 Redis 和 Milvus 健康后启动。应用健康检查验证 Web 服务可访问，真实模型调用需要有效的 API Key，可在页面上传论文并发起问答验证。

Dockerfile 使用 Maven / Java 17 多阶段构建，构建时执行现有测试，运行镜像以 UID 10001 非 root 用户启动。默认激活 `application-docker.yml`，容器通过服务名连接数据库。实现参考 [Docker 多阶段构建](https://docs.docker.com/build/building/multi-stage/)和 [Compose 启动顺序](https://docs.docker.com/compose/how-tos/startup-order/)。

| `.env` 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 必填 | 模型与向量化调用 |
| `SCHOLARMIND_BIND_ADDRESS` | `127.0.0.1` | 宿主机监听地址 |
| `SCHOLARMIND_PORT` | `9900` | Web 访问端口 |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70.0` | JVM 参数 |

### 数据与常用操作

| 命名卷 | 保存内容 |
| --- | --- |
| `runtime-data` | H2 运行状态、检查点、会话与记忆元数据 |
| `papers` | 上传的论文 |
| `workspace` | Agent 文件工具可访问的工作目录 |
| `redis-data` | 工作记忆 |
| `etcd-data`、`minio-data`、`milvus-data` | 向量库元数据与存储 |

```bash
# 查看应用日志
docker compose logs -f app
# 查看向量库日志
docker compose logs --tail=100 milvus
# 停止容器并保留数据卷
docker compose down
# 更新代码后重新构建应用
git pull --ff-only
docker compose up -d --build --wait --wait-timeout 300
```

数据卷独立于本地运行的 `data/`、`papers/` 和旧 `vector-database.yml` 的 `volumes/`，不会自动迁移已有数据。备份时先停止写入，再备份全部相关卷；不要同时启动多个应用实例共享同一个 H2 数据卷。`down` 不要添加删除数据卷的参数，除非确实需要丢弃所有数据。

Skills 已打包进镜像，修改后重新构建即可生效。Agent 工作目录默认是空的数据卷，不包含宿主机项目源码；需要查询仓库时，可在 Compose override 中把目标目录只读挂载到 `/app/workspace`。论文上传使用独立的 `/app/papers`。

### MCP 与故障排查

默认容器关闭外部 MCP，内置工具与 Skills 可用。现有 `application-mcp.yml` 使用 Windows 的 `cmd.exe`，不能直接用于 Linux 容器；若需容器 MCP，请单独提供 Linux 配置和包含 Node.js/uv 等依赖的扩展镜像。默认镜像不提供 Docker CLI，也不挂载宿主机 Docker socket。

- `DASHSCOPE_API_KEY` 报错：检查根目录 `.env` 是否填写；终端中同名环境变量会优先于 `.env`。
- 服务等待超时：运行 `docker compose ps` 和 `docker compose logs --tail=100`，检查镜像下载、可用内存及 Milvus/MinIO/etcd 状态。修复后重试启动。
- 端口被占用：修改 `.env` 的 `SCHOLARMIND_PORT`，重新运行启动命令。
- 自定义宿主机目录挂载出现权限错误：确保应用 UID 10001 对数据目录有写权限。
- 仅在宿主机运行 Java 应用时，继续使用下面的 `vector-database.yml` 流程；完整容器部署使用本节 `compose.yaml`，两套数据相互独立。

## 混合检索与重排序

论文检索默认启用三段式链路：Milvus 稠密向量召回、内置 BM25 关键词召回、RRF（Reciprocal Rank Fusion）融合，然后调用 `BAAI/bge-reranker-v2-m3` API 对融合候选精排。结果包含 `retrievalSources`、`denseRank`、`keywordRank`、`denseScore`、`keywordScore`、`fusionScore`、`rerankScore`、`rerankStrategy` 字段；`score` 为最终重排序分数，数值越高排名越靠前。

BM25 索引在每次论文上传并完成向量入库后更新，服务启动时也会根据 `papers/` 目录中的论文重建。因此，升级后重启服务即可为保留在上传目录中的旧论文补齐关键词索引；仅存在于 Milvus、但已不在上传目录中的旧数据不会进入 BM25 索引。

```yaml
rag:
  hybrid:
    enabled: true          # false 时回退为仅 Milvus 向量检索
    candidate-top-k: 20    # 每个召回器的候选数
    rrf-k: 60              # RRF 常数
    rerank-top-k: 12       # 参与重排序的融合候选数
    rerank:
      base-url: ${BGE_RERANK_BASE_URL:} # 例如 http://reranker-host:8000/v1
      api-key: ${BGE_RERANK_API_KEY:}
      model: BAAI/bge-reranker-v2-m3
      timeout-ms: 30000
      fallback-rrf-weight: 0.7
```

配置 `BGE_RERANK_BASE_URL` 后，应用会向 `{base-url}/rerank` 发送 `model`、`query` 和 `documents`，并接受常见的 `results[index,relevance_score]` 或 `results[index,score]` 返回格式。接口应托管 `BAAI/bge-reranker-v2-m3`，例如兼容 `/v1/rerank` 的 vLLM 服务。该模型为多语言 query-document cross-encoder；模型卡和用法见 [BAAI 官方模型页](https://huggingface.co/BAAI/bge-reranker-v2-m3)。

`GET /api/papers/search?query=...&topK=...`、RAG 问答、Agent 的论文知识库工具和 Runtime 上下文证据均使用该混合链路。向量数据库或 Embedding 服务不可用时，系统会记录告警并返回可用的 BM25 结果；BM25 本身依赖上传目录中的可解析论文。

如果 BGE API 未配置、超时、返回非 2xx 或结果缺少候选分数，系统才会使用本地词项覆盖与完整短语匹配重排序降级，并在 `rerankStrategy` 中返回 `LOCAL_LEXICAL_FALLBACK`。BGE API 成功时该字段为 `BGE_RERANKER_V2_M3`。这避免了精排服务的短暂故障阻断论文检索。

## 本地运行

需要 Java 17、Maven、Docker，以及 DashScope API Key。Redis 用于工作记忆，无法连接时会降级使用持久化会话记忆。

```powershell
$env:DASHSCOPE_API_KEY = '填写你的 API Key'
docker compose -f vector-database.yml up -d
mvn spring-boot:run
```

打开 <http://localhost:9900> 上传论文并开始研究。默认使用本地 H2 数据库，数据保存在 `data/`；上传文件保存在 `papers/`。MySQL 可通过 `application-mysql.yml` 配置。

MCP 默认关闭。安装 Node.js/npx、Docker 和 uv，配置 GitHub token 及 MCP 文件目录后，可启用：

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=mcp'
```

MCP 连接定义见 `src/main/resources/application-mcp.yml`。现有命令面向 Windows；其他系统需调整启动命令和目录。

GNU Make 用户可运行 `make help`。上传单篇论文使用 `make upload FILE=/path/to/paper.pdf`；初始化不会导入运维文档。

## 主要接口

| 接口 | 用途 |
| --- | --- |
| `POST /api/upload` | 上传论文，multipart 字段 `file` |
| `POST /api/chat`、`POST /api/chat_stream` | 问答，JSON 字段 `Id`、`Question` |
| `POST /api/research/workflow` | 研究工作流，JSON 字段 `SessionId`、`Task` |
| `POST /api/agent-runs` | 执行任务，字段 `executionType`、`input`、`sessionId` |
| `GET /api/agent-runs/{runId}` | 查询运行状态 |
| `POST /api/agent-runs/{runId}/resume` | 从检查点恢复 |
| `GET /api/agent-runs/{runId}/checkpoints`、`GET /api/agent-runs/{runId}/traces` | 检查点与运行轨迹 |
| `GET /api/papers/search` | 检索论文知识库 |
| `GET /api/tools/mcp`、`GET /api/skills` | 工具与 Skills |
| `/api/memory` | 会话与语义记忆，详见 `docs/context-memory.md` |
| `GET /milvus/health` | 向量数据库健康检查 |
