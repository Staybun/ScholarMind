# 待手动清理文件

项目规则禁止批量删除，因此本次未删除文件，也未清空文件来绕过此限制。

## 确认脱离当前论文研究流程的文件

以下路径均相对于项目根目录；源代码检索未发现对两个退役工具类或 DropCollection 的其他引用。

| 文件 | 原因 |
| --- | --- |
| `aiops-docs/cpu_high_usage.md` | 旧 CPU 运维知识文档 |
| `aiops-docs/disk_high_usage.md` | 旧磁盘运维知识文档 |
| `aiops-docs/memory_high_usage.md` | 旧内存运维知识文档 |
| `aiops-docs/service_unavailable.md` | 旧服务故障知识文档 |
| `aiops-docs/slow_response.md` | 旧响应延迟知识文档 |
| `src/main/java/org/example/agent/tool/QueryLogsTools.java` | 无 Spring 注解的退役占位类 |
| `src/main/java/org/example/agent/tool/QueryMetricsTools.java` | 无 Spring 注解的退役占位类 |
| `src/main/java/org/example/tool/DropCollection.java` | 独立 main 程序，删除旧 biz 集合，不属于论文业务流程 |

请手动删除上述不再需要的文件。占位类此前用于覆盖旧编译类，因此删除源码后，也需在停止应用后手动清理旧 target 构建产物，再重新编译，避免遗留类进入后续构建。

## 保留范围

- `runtime/`、`context/`、`memory/`、论文服务、MCP、Skills 及其测试：对应目标功能。
- Web 页面、上传、会话、数据库健康检查及数据库配置：支持目标功能的基础设施。
- `data/`：包含真实运行与记忆数据库，不作为无用文件删除。锁文件需由正常关闭数据库释放。
- `papers/`（若产生）：用户论文资料，不删除。
- `LICENSE`、来源记录及技术注释：保留。
- `.idea/` 和 `target/`：本地 IDE/构建产物，可能仍有旧名称，不属于当前业务源码；已被忽略。可按需手动清理并重新导入 Maven 项目。

当前论文 RAG 只有向量检索，未发现混合检索与独立 Rerank 实现。本次为清理工作，不补写这些功能，也不将其描述为已完成。
