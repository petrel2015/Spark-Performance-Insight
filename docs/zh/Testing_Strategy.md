# 测试策略与“有意义的覆盖”

本文档介绍了 Spark Performance Insight 的测试哲学及其技术实现。

## 🎯 核心哲学：有意义的覆盖 (Meaningful Coverage)

在本项目中，我们优先考虑**覆盖率的质量而非数字总量**。我们遵循“有意义的覆盖”原则，确保 CI/CD 流水线能真正保护业务逻辑，而不是仅仅统计样板代码。

### 为什么我们在 JaCoCo 中排除了某些模块？

`pom.xml` 配置明确排除了一些包（Models, Mappers, Configs 等）。这是一个刻意的架构决策，基于以下考虑：

1.  **信号与噪声 (Signal vs. Noise)**: 
    *   **Models/DTOs**: 这些大多是由 Lombok 处理的数据容器。测试 Getter/Setter 没有价值，且会“稀释”覆盖率指标。
    *   **Configs**: Spring 配置类是声明式的。测试它们通常变成了“测试框架本身”，这是冗余的。
    *   **Mappers**: MyBatis 接口的逻辑定义在 XML SQL 中。纯 Java 单元测试无法在没有真实数据库的情况下有效运行这些 SQL。

2.  **聚焦“大脑”**:
    *   通过排除样板代码，我们将 **70% 行覆盖率** 和 **80% 分支覆盖率** 的阈值严格应用于 **核心服务层** (`DiagnosisService`, `ComparisonService`) 和 **工具类** (`FormatUtils`)。
    *   如果覆盖率低于这些阈值，意味着有**关键的业务逻辑**失去了保护。

3.  **SQL 驱动架构**:
    *   由于本项目大量逻辑存在于 DuckDB SQL 中，Java 单元测试侧重于**数据转换**和**决策逻辑**。
    *   对于 SQL 逻辑本身，我们依赖集成测试和针对示例 EventLog 的手动验证。

## 🛠 技术栈

### 后端 (Java)
- **框架**: JUnit 5 + Mockito.
- **覆盖率**: JaCoCo (带有自定义排除过滤器)。
- **阈值**: 70% 行覆盖率, 80% 分支覆盖率 (针对包含的模块)。

### 前端 (TypeScript/Vue)
- **框架**: Vitest + @vue/test-utils.
- **覆盖范围**: 聚焦于 `src/store` (状态管理) 和 `src/utils` (格式化/解析)。

## 🚀 持续集成 (CI)

每一次推送 (Push) 和合并请求 (PR) 都会触发 `.github/workflows/ci.yml` 中定义的 GitHub Action。如果满足以下任一条件，构建将**失败**：
1.  任一测试用例未通过。
2.  未能达到“有意义的覆盖”阈值。

## 🧪 手动连通性校验

对于依赖外部供应商的功能（如 LLM），我们提供了一个轻量级的验证路径：

*   **LLM 连通性**: `src/test/java/com/fluffyeti/spark/performance/insight/llm/LLMManualConnectionTest.java`。
*   **目的**: 允许用户在不启动完整应用或数据库的情况下，验证其 API Key 和网络连通性。
*   **使用方法**: 在本地变量中填入你的 API Key，并通过 IDE 手动运行。这些测试默认使用 `@Disabled` 标记，以避免在 CI 环境中因缺少 Key 而报错。

## 🏗️ 手动集成测试 (Manual Integration Tests)

由于 DuckDB Native 库的初始化限制（防止在自动化多类测试期间频繁刷新 JVM），所有依赖数据库的测试都隔离在 `src/test/manual/` 目录中。

### 为什么要隔离？
如果 DuckDB 的 Native 驱动在同一个 Maven 进程中被多次重新初始化或并发调用，可能会触发 JVM 崩溃（`Abort Trap 6`）。

### 如何运行这些测试？
这些测试对于验证 **SQL-Schema 兼容性** 和 **端到端解析逻辑** 至关重要：
1.  **IDE 运行**: 大多数现代 IDE（IntelliJ IDEA, Eclipse）可以直接从 `src/test/manual` 目录运行这些测试。只需右键点击并选择 “Run” 即可。
2.  **命令行运行**: 临时将所需的测试文件移回 `src/test/java/...` 目录下，并执行 `mvn test -Dtest=测试类名`。请务必在提交代码前将其移回。

---

*该策略确保了随着项目的增长，我们的维护精力能始终集中在真正核心的逻辑上。*
