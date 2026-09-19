# ScholarMind — 学术论文 Agent 系统

ScholarMind 面向科研论文研读与实验复现规划，提供论文知识问答、研究任务编排、工具调用和分层上下文记忆。

## 功能与代码

| 功能 | 实现位置与范围 |
| --- | --- |
| Agent Runtime Harness | `runtime/`：Run 状态、持久化 Checkpoint、Resume、Retry、Trace |
| 论文知识库 | `service/`：PDF、Markdown、TXT 解析，分片、向量化、Milvus 向量检索与 RAG |
| 单 Agent 与多 Agent | ReAct、Plan-Execute 执行器；Supervisor 协调论文检索、研究问答、实验规划 Agent |
| MCP 与 Skills | 文件系统、GitHub、arXiv MCP 配置；论文阅读与实验复现 Skills |
| 上下文与记忆 | `context/`、`memory/`：Token Budget、滑动窗口、历史摘要、Redis 工作记忆、持久化会话及语义记忆 |


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
