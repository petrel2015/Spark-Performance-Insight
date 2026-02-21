---
name: alibaba-java-guidelines
description: Expert guidance on Alibaba Java Coding Guidelines. Use when you need to check Java code compliance, refactor code to meet Alibaba standards, or answer questions about Alibaba Java development practices.
---

# Alibaba Java Guidelines

## Overview

This skill enables Gemini CLI to enforce and apply the Alibaba Java Coding Guidelines. It covers programming specifications (naming, constants, formatting, OOP, collections, concurrency, flow control), exception handling, logging, MySQL usage, project layering, and security.

## Guidelines Reference

The full set of guidelines is available in [references/guidelines.md](references/guidelines.md). When performing a check or refactoring, refer to this file for specific "Mandatory", "Recommended", and "Reference" rules.

## Core Workflows

### 1. Code Compliance Check

When asked to check Java code for compliance:
1.  Read the target Java file(s).
2.  Search [references/guidelines.md](references/guidelines.md) for relevant rules (Naming, OOP, Concurrency, etc.).
3.  Identify violations, classifying them by severity (Mandatory, Recommended, Reference).
4.  Provide a report with specific line numbers and the corresponding rule from the guidelines.

### 2. Code Refactoring

When asked to refactor Java code to comply with Alibaba standards:
1.  Analyze the existing code against [references/guidelines.md](references/guidelines.md).
2.  Apply mandatory changes first (e.g., naming, magic values, thread safety).
3.  Apply recommended changes (e.g., using Optional, guard statements, StringBuilder in loops).
4.  Ensure the refactored code maintains the same business logic and is idiomatic Java.
5.  Validate the changes (if tests are available).

### 3. Answering Technical Questions

When asked a question about Alibaba Java standards:
1.  Identify the specific domain of the question (e.g., "How to name constants?").
2.  Locate the relevant section in [references/guidelines.md](references/guidelines.md).
3.  Provide a concise answer, citing whether the rule is Mandatory or Recommended, and include positive/counter examples from the reference.

## Example Triggers

- "Does this class follow Alibaba's naming conventions?"
- "Refactor this method to comply with Alibaba Java guidelines."
- "What is the Alibaba standard for handling NullPointerExceptions?"
- "Review my MySQL table schema according to Alibaba's rules."
- "How should I structure my application layers based on the Alibaba manual?"
