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
- **Decision:**
    - If there are too many unrelated changes, plan to split them into multiple commits.
    - If there are untracked files, decide whether to add them or ignore them.

### 2. Group Changes (Atomic Commits)
**Goal:** Create commits where each one does one thing and does it well.

- **Strategy:** Group files by feature, fix, or refactor.
    - *Example:* All changes related to "fixing the login bug" go in one commit.
    - *Example:* All "database schema renames" go in another commit.
- **Avoid:** "Kitchen sink" commits that mix formatting, features, and bug fixes.

### 3. Stage and Commit
**Goal:** Commit each group sequentially.

**Loop for each logical group:**
1.  **Stage:** Use `git add <file1> <file2> ...` to stage only the relevant files for this specific commit.
    - *Tip:* Use `git add -p` (patch mode) if you need to split changes within a single file (though CLI agents might prefer file-level granularity for simplicity).
2.  **Verify:** (Optional but recommended) Run `git diff --staged --name-only` to confirm you staged what you intended.
3.  **Commit:** Run `git commit -m "<type>: <subject>"`
    - **Convention:** Use Conventional Commits format:
        - `feat:` for new features
        - `fix:` for bug fixes
        - `docs:` for documentation
        - `style:` for formatting (missing semi-colons, etc)
        - `refactor:` for code restructuring without behavior change
        - `test:` for adding tests
        - `chore:` for maintenance (build tasks, package managers)
    - **Message:** Keep the subject line under 50 chars if possible. Use the body for details if necessary (for `git commit`, usually just `-m` is enough, but multiple `-m` flags can add a body).

### 4. Final Review
**Goal:** Ensure everything is clean.

- **Action:** Run `git status` one last time to ensure no files were left behind unintentionally.
- **Action:** Run `git log -n <number_of_new_commits>` to show the user the result.

## Example Session

**User:** "Commit these changes."

**Gemini CLI:**
1.  `git status` -> Sees `auth.ts`, `auth.test.ts`, `README.md`, `logo.png`.
2.  **Plan:**
    - Commit 1: Auth feature (`auth.ts`, `auth.test.ts`)
    - Commit 2: Documentation (`README.md`)
    - Commit 3: Assets (`logo.png`)
3.  **Execution:**
    - `git add auth.ts auth.test.ts`
    - `git commit -m "feat: implement user authentication"`
    - `git add README.md`
    - `git commit -m "docs: update readme with auth instructions"`
    - `git add logo.png`
    - `git commit -m "chore: add company logo"`
4.  **Report:** "I have split your changes into 3 commits..."
