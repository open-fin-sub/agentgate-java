# AgentGate Java 重构工程说明（AGENTS.md）

本工程是 Python 版 AgentGate 评测平台（`/Users/eric/hw/xql/pythonwork/agentgate`，行为金标准，默认只读）的平台服务面 Java 重构。详细协作规则见 `/Users/eric/hw/xql/document/abc-北京农行/评测系统JAVA重构/重构设计/AGENTS.md`；冲突时以用户当次指示与详细版为准，两份文件更新时保持同步。

## 总体目标与终态架构

**目标**：在 Python 服务端（行为金标准，持续更新）基础上重构一版**功能完全等价**的 Java 服务端；两套后端**长期并存**，通过多轮比对保持等价。终态：**一个前端、一套数据库、两套后端**，任务执行延续 Python 独立进程。

```
                ┌─────────┐
                │  浏览器  │
                └────┬────┘
                     │
        ┌────────────▼────────────┐
        │  前端页面 ×1（不迁移）     │
        │  .env.local 重新构建切换   │
        └────┬───────────────┬────┘
             │               │
   ┌─────────▼─────┐   ┌─────▼─────────┐
   │ Python 后端    │   │ Java 后端      │
   │ 持续更新·金标准 │◄─►│ 功能完全等价    │
   └───────┬───────┘   └───────┬───────┘
           └────────┬──────────┘
                    │ 共库共用数据
           ┌────────▼────────┐
           │  数据库 ×1        │
           │  MySQL / TDSQL   │
           └────────▲────────┘
                    │ 读写
   ┌────────────────┴───────────┐
   │ 任务执行进程 ×1（独立部署，    │
   │ 延续 Python 不重构；BJS 回调  │
   │ run.sh 拉起）               │
   └────────────────────────────┘
```

- 前端切换为**构建期切换**（改 `.env.local` 重建），不做运行时一键切换。
- 多轮比对三级机制：① 契约级 golden 对拍（Python 更新 → 重生成基线 → Java 回归）→ ② **API 级自动化对比工具【建设项】**（同一请求双后端分发、逐字段比对响应、全端点批量、差异报告，形态另行专项设计）→ ③ 行为级前端切换逐页对照。
- Python 服务端的更新是持续输入；每轮更新走一遍比对循环。

## 范围

- ✅ Java：HTTP API、CLI、dataset、result 读取侧、optimizer、skill_analysis、OTLP 接收 `/v1/traces`、脱敏（读取侧）、调度扫描 + BJS 提交、domain/storage Java 版、fail_stale_runs。
- 🐍 不动：BJS 回调拉起的 Python 执行链（引擎/evaluator/targets/judge/abc_llm_sdk；含 trace 执行内归一化——evaluator 执行侧的 `Trace.for_turn`/`completion_sequence` 等视图辅助，Java domain 不迁移，需要时再议）；主力前端 = Python 仓库 `frontend/`（agent-evaluation-ux），**不迁移**、仅改其 `.env.local` 指向 Java（`API_PROXY_TARGET` 指向 Java 服务 + `VITE_API_BASE_URL=/race-api/api`）；Python 仓库 `web/` 为测试页面，**忽略**；本工程 `frontend/` 为框架验证样板，后续废除。
- 📌 trace 归一化归属：OTLP→Trace 的**接收归一化**（Python `ingest_otlp_json`，server 依赖层）归 Java（`/v1/traces` 端点的一部分）；执行内视图逻辑归 Python。
- 🗑 退役件不迁移：Celery worker/beat、内置 Redis、redis_cluster_transport。
- 🔗 与 Python 共库的跨语言契约：canonical JSON + content_sha256、原子 claim、凭据密文 AES-256-GCM `v1.{base64}`、run 状态机、trace 归一化——必须与 Python 语义一致并编写 golden 对拍测试。
- 已决策（2026-10-01）：LLM 通道暂不支持 `transport=api`，skill_analysis/optimizer 的 LLM 调用点返回 mock + TODO，后期替换；调度扫描归 Java `@Scheduled`，BJS 只负责执行；前端直连 Java（无代理层），context-path 保留 `/race-api`；user-context（header `user_team_id`/`user_id`/`user_name`）读取延后至收尾阶段统一处理。
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
- 只用 Java 8 语法（无 `var`/`record`/`sealed`）；不可变值对象用 Lombok `@Value`/`@Builder`。实践红线：`String.repeat()` 是 Java 11 API 禁用；`Arrays.asList(数组)` 泛型陷阱（需 `Arrays.<T[]>asList`）；空容器的 `clear()` 是静默 no-op（不可变性断言用 `add()`/`put()`）。
- 分层：Controller（校验 → Service → `ResponseBase<T>`，`code="0"` 成功）→ Service → Logic（跨表聚合、事务边界）→ DAO（单表 CRUD，复杂 SQL 走 XML）。
- 包结构：`com.abchina.llmalf.agentgate` 下**按层平铺**——`controller/`、`service/`（含 `impl/`、`vo/`）、`logic/`、`dao/`（含 `entity/`，XML 与 DAO 同包）、`enums/`，不按业务建子包；`domain/` 为顶层纯 Java 契约层（canonical JSON、content_sha256、值对象与校验，不依赖 Spring/MyBatis）；`common/`、`config/` 为跨层通用件；`user` 为框架验证样板（后续废除，勿参照其建立业务子包）。Python 模块名 → Java 包名：下划线驼峰化（`evaluation_task`→`evaluationtask`），Java 关键字取复数（`case`→`cases`）。
- 领域对象（`domain/model`）管校验与 canonical payload 组装；持久化实体（`dao/entity`，`@TableName("agentgate_*")`）只做行映射；Logic 层负责两者互转。agentgate_* 表为**薄列 + payload** 模式（业务全量进 `payload` LONGTEXT，内容寻址表带 `content_sha256`）；`id_key`/`user_team_key` 为 `BINARY(32)` SHA-256 身份摘要，必须与 Python 逐字节一致（golden 对拍项）；表内无 `is_deleted`，不用 `@TableLogic`（archived 业务语义）。
- **domain 模型规范**：每个模型自带 `of(...)` 全参工厂（构造期全量校验，消息与 Python 逐字一致）+ `fromPayload(Map)`/`toPayload()` 双向 API（字段序对齐 pydantic 声明序，缺省补默认、缺 id 生成 UUID）；判别联合 = 接口/抽象基类 + `kind()` + `fromPayload` 分发，实现类解析方法命名 `parse` 且包私有；小值域 Literal 用 String + 值域校验，枚举 `fromWireValue` 抛 pydantic StrEnum 文本（历史例外：`RunStatus`）；可选文本两派——`if value else None`（空串归一 null）用 `PayloadValues.normalizeOptionalText`，`if value is not None`（空串报错）用 `optionalString`+非空白校验，逐字段核对 Python 源码；时间戳 payload 唯一出口 `DomainValidations.isoFormat`（Z 后缀、微秒 6 位或省略、纳秒截断），`normalizeUtc` 前缀按 Python 源码核对；共享校验放包私有工具类（`EvaluatorDefinitions`/`TargetDescriptors`）；容器构造期深冻结并拷贝源集合。
- **golden 对拍规范**：脚本 `scripts/generate_*_golden.py`（只读调 Python venv、固定 seed、自检断言），产物 `src/test/resources/contract/*.json`；合法样本 payload/canonical/sha256 三重对拍，非法样本消息分级（自定义与 pydantic 内建逐字复刻；判别错误/datetime 细节后缀标 `message_alignable=false` 仅断言抛出）；Python 无法 JSON 表达的输入不进 golden，Java 单测覆盖；金标准已知限制——Python `Any` 字段含容器值时 `model_dump` 失败（`Equals.expected`/`OneOf.allowed` 容器值），Java `toPayload` 支持序列化，golden 不含此场景；嵌套子模型错误 golden 取第一条。
- 业务异常抛 `AgentException`，由 `GlobalExceptionHandler` 统一捕获。
- 实体继承 `Serializable`，`@TableName`/`@TableId`/`@TableField`/`@TableLogic` 齐全；TDSQL 保留字反引号包裹；MyBatis XML 与 DAO 同包。
- 日志用 `@Slf4j`，关键节点结构化打点；禁止记录任何密钥/凭据；不引入新日志框架。
- 环境相关或可调的常量一律做成 `application.yml` 配置项（`@Value("${key:默认值}")`，默认值可写死在代码，yml 中显式列出）；仅跨语言协议契约常量（`/v1/traces`、脱敏正则、信封码）可固化在代码。
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
