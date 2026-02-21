# Testing Strategy & Meaningful Coverage

This document outlines the testing philosophy and technical implementation for Spark Performance Insight.

## 🎯 Core Philosophy: Meaningful Coverage

In this project, we prioritize **quality of coverage over numerical volume**. We follow the principle of "Meaningful Coverage" to ensure our CI/CD pipeline acts as a true guardian of business logic rather than a reporter of boilerplate code.

### Why we exclude certain modules from JaCoCo?

The `pom.xml` configuration explicitly excludes several packages (Models, Mappers, Configs, etc.). This is a deliberate architectural decision based on the following:

1.  **Signal vs. Noise**: 
    *   **Models/DTOs**: These are mostly data containers (POJOs) handled by Lombok. Testing Getters/Setters adds no value and "dilutes" the coverage metric.
    *   **Configs**: Spring configuration classes are declarative. Testing them usually results in "testing the framework," which is redundant.
    *   **Mappers**: MyBatis interfaces define logic in XML SQL. Pure Java unit tests cannot execute these SQLs meaningfuly without a live database.

2.  **Focus on "The Brain"**:
    *   By excluding boilerplate, we force the **70% Line Coverage** and **80% Branch Coverage** thresholds to apply strictly to **Core Services** (`DiagnosisService`, `ComparisonService`) and **Utility Classes** (`FormatUtils`).
    *   If coverage drops below these thresholds, it means a **critical piece of business logic** is unprotected.

3.  **SQL-Heavy Architecture**:
    *   Since much of our logic resides in DuckDB SQL, our Java unit tests focus on **Data Transformation** and **Decision Logic**.
    *   For the SQL logic itself, we rely on integration tests and manual validation against sample EventLogs.

## 🛠 Technical Stack

### Backend (Java)
- **Framework**: JUnit 5 + Mockito.
- **Coverage**: JaCoCo (with custom exclusion filters).
- **Thresholds**: 70% Line, 80% Branch (on included modules).

### Frontend (TypeScript/Vue)
- **Framework**: Vitest + @vue/test-utils.
- **Coverage**: V8.
- **Scope**: Focused on `src/store` (State Management) and `src/utils` (Formatting/Parsing).

## 🚀 Continuous Integration

Every Push and Pull Request triggers the GitHub Action defined in `.github/workflows/ci.yml`. The build will **fail** if:
1.  Any test case fails.
2.  The "Meaningful Coverage" thresholds are not met.

## 🧪 Manual Connectivity Checks

For features that rely on external providers (like LLMs), we provide a lightweight verification path:

*   **LLM Connectivity**: `src/test/java/com/fluffyeti/spark/performance/insight/llm/LLMManualConnectionTest.java`.
*   **Purpose**: Allows users to verify their API keys and network connectivity without running the full application or database.
*   **Usage**: Fill in your API keys in the local variables and run the test manually from your IDE. These tests are `@Disabled` by default to avoid CI failures.

## 🏗️ Manual Integration Tests

Due to DuckDB's Native library initialization constraints (preventing frequent JVM refreshes during automated multi-class testing), all database-dependent tests are isolated in the `src/test/manual/` directory.

### Why isolation?
DuckDB native drivers can trigger JVM crashes (`Abort Trap 6`) if re-initialized too frequently or concurrently within the same process during a Maven build.

### How to run them?
These tests are essential for validating **SQL-Schema compatibility** and **End-to-End parsing logic**:
1.  **IDE Execution**: Most modern IDEs (IntelliJ IDEA, Eclipse) can run these tests directly from the `src/test/manual` directory. Simply right-click and "Run".
2.  **Command Line**: Temporarily move the desired test file back to `src/test/java/...` and run `mvn test -Dtest=YourTestName`. Remember to move it back before committing.

---

*This strategy ensures that as the project grows, our maintenance effort remains focused on the logic that actually matters.*
