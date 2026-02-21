---
name: smart-commit
description: Workflow for analyzing code changes and creating atomic, logical commits. Use when the user asks to "commit", "submit code", "save changes", or split changes into multiple commits.
---

# Smart Commit

## Overview

This skill guides the process of creating high-quality, atomic Git commits. It ensures that changes are logically grouped, commit messages are descriptive and follow conventions, and the history remains clean and understandable.

## Workflow

### 1. Analyze Status
**Goal:** Understand the current state of the repository.

- **Action:** Run `git status` to see staged, unstaged, and untracked files.
- **Action:** Run `git diff --name-only` (and `git diff` if needed) to understand the nature of the changes.

### 2. Pre-Commit Verification (Mandatory Guardrail)
**Goal:** Ensure code quality before any commit.

- **Requirement:** Before staging any files, you MUST run all backend and frontend tests.
- **Backend Test:** Run `mvn clean test`.
- **Frontend Test:** Run `cd frontend && npm run test:coverage`.
- **Constraint:** If any test fails, you MUST STOP and report the failures to the user. Do NOT commit code with failing tests unless explicitly instructed by the user to bypass.

### 3. Group Changes (Atomic Commits)
**Goal:** Create commits where each one does one thing and does it well.

- **Strategy:** Group files by feature, fix, or refactor.
- **Avoid:** "Kitchen sink" commits that mix formatting, features, and bug fixes.

### 4. Stage and Commit
**Loop for each logical group:**
1.  **Stage:** Use `git add <file1> <file2> ...` to stage only the relevant files.
2.  **Verify:** Run `git diff --staged --name-only` to confirm.
3.  **Commit:** Run `git commit -m "<type>: <subject>"` (using Conventional Commits).

### 5. Final Review
- **Action:** Run `git status` to ensure a clean working tree.
- **Action:** Run `git log -n <number_of_new_commits>` to show the result.

## Example Session

**User:** "Commit these changes."

**Gemini CLI:**
1.  `git status` -> Sees changes in `DiagnosisService.java` and `format.ts`.
2.  **Verify:** Runs `mvn test` and `npm test`. Both pass.
3.  **Plan:**
    - Commit 1: Fix diagnosis logic (`DiagnosisService.java`)
    - Commit 2: Refactor format utils (`format.ts`)
4.  **Execution:**
    - `git add ...`
    - `git commit -m "fix: correct scoring algorithm in DiagnosisService"`
    - ...
5.  **Report:** "Tests passed. I have created 2 commits..."
