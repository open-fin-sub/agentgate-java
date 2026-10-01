# AgentGate Java 重构工程说明（AGENTS.md）

本工程是 Python 版 AgentGate 评测平台（`/Users/eric/hw/xql/pythonwork/agentgate`，行为金标准，默认只读）的平台服务面 Java 重构。详细协作规则见 `/Users/eric/hw/xql/document/abc-北京农行/评测系统JAVA重构/重构设计/AGENTS.md`；冲突时以用户当次指示与详细版为准，两份文件更新时保持同步。

## 范围

- ✅ Java：HTTP API、CLI、dataset、result 读取侧、optimizer、skill_analysis、OTLP 接收 `/v1/traces`、脱敏（读取侧）、调度扫描 + BJS 提交、domain/storage Java 版、fail_stale_runs。
- 🐍 不动：BJS 回调拉起的 Python 执行链（引擎/evaluator/targets/judge/abc_llm_sdk）；前端 `frontend/`（Vue 3，仅本地联调，API 契约不变）。
- 🗑 退役件不迁移：Celery worker/beat、内置 Redis、redis_cluster_transport。
- 🔗 与 Python 共库的跨语言契约：canonical JSON + content_sha256、原子 claim、凭据密文 AES-256-GCM `v1.{base64}`、run 状态机、trace 归一化——必须与 Python 语义一致并编写 golden 对拍测试。
- 范围基线为《重构分析V2.md》三色分类，最终逐模块与用户确认后定稿。

## 工作流程

- 每步先出中文方案（行为意图、变更文件、验证命令），用户确认后才写代码；调研、解释、提方案不等于批准。
- 四步检查点，逐步确认：名称与结构 → 职责 → 详细设计 → 实现。
- 动手前报告 `git status --short` 与当前分支；文件级评审一次只处理一个文件。

## 验证

- 先聚焦：`mvn test -Dtest=XxxTest`；通过后全量：`mvn test`；两者结果都要报告。
- 不留编译错误与未处理的编译告警。

## 工程约束

- Java 8 + Spring Boot 2.3.8 + MyBatis-Plus 3.5.5（版本锁定见 pom）+ Lombok。
- 存储仅支持 MySQL/TDSQL，不实现 SQLite；开发/测试使用 Python 工程 `.env`（`AGENTGATE_TDSQL_URL`）指向的 MySQL 库，与 Python 侧共库共用数据。
- **Schema 冻结**：严禁新增表、字段或任何数据库结构变更，一律按现有数据库表结构重写；后续新增功能需改库时，必须先经用户同意。
- 只用 Java 8 语法（无 `var`/`record`/`sealed`）；不可变值对象用 Lombok `@Value`/`@Builder`。
- 分层：Controller（校验 → Service → `ResponseBase<T>`，`code="0"` 成功）→ Service → Logic（跨表聚合、事务边界）→ DAO（单表 CRUD，复杂 SQL 走 XML）。
- 模块结构：`com.abchina.llmalf.agentgate.<module>/{controller, service(含 impl/vo), logic, dao(含 entity), enums, util}`；参照现有 `user`、`common/`、`config/` 样板。
- 业务异常抛 `AgentException`，由 `GlobalExceptionHandler` 统一捕获。
- 实体继承 `Serializable`，`@TableName`/`@TableId`/`@TableField`/`@TableLogic` 齐全；TDSQL 保留字反引号包裹；MyBatis XML 与 DAO 同包。
- 日志用 `@Slf4j`，关键节点结构化打点；禁止记录任何密钥/凭据；不引入新日志框架。
- 新增依赖、抽象层、兼容别名须先经用户确认。
- 编码规范全文：`/Users/eric/hw/xql/document/abc-北京农行/工程架构与编码规范（已脱敏）.md`。

## Git

- 未经用户明确要求，不做任何 git 写操作（commit/push/merge/rebase/建分支/切分支）。
- feature 工作流：新功能从最新基线建 `feature/<feature-name>` 分支；验证通过且用户明确批准后才合并。
- 不做破坏性操作；Python 仓库只读。

## 文档

- 设计文档与进度记录统一放本仓库 `docs/`（目录结构与命名首次写入前与用户确认）。

## 沟通

- 中文用于协作、方案、汇报；英文用于源代码、标识符、commit message。
- 架构/重构类进度回复以 `Where are we` 块开头。
- 需求歧义、规范未覆盖的架构/产品选择、需外部访问或凭据时：暂停并询问用户。
