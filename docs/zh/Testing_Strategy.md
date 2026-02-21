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

---

*该策略确保了随着项目的增长，我们的维护精力能始终集中在真正核心的逻辑上。*
