---
name: agent-team
description: Multi-agent orchestration workflow for tasks that should be split across one tech-lead agent plus backend developer, QA developer, and reviewer agents. Use when the work benefits from explicit planning, serial/parallel delegation, progress reporting, and role separation instead of a single agent doing everything.
---

# Agent Team

## Purpose

- Decompose complex development tasks into role-based agent teams, separating planning, implementation, QA, and critical review.
- The Tech Lead agent orchestrates the entire flow, while specialized role agents perform the actual work.

## Team Composition

- Tech Lead Agent: exactly 1
- Backend Developer Agent: 0 to 5
- QA Developer Agent: 0 to 5
- Reviewer Agent: 1 to 5

Each role has the following responsibilities:

- Tech Lead Agent
  - Reads user requirements and related documents, then creates a plan to achieve the goal.
  - Invokes Backend Developer, QA Developer, and Reviewer agents in serial or parallel according to the plan.
  - Reviews each agent's results and decides the next instructions.
  - Outputs a brief progress update to the user after each individual task completes.
  - Performs orchestration only.
  - On agent task failure, analyzes the root cause and autonomously decides follow-up actions such as retry, reassignment, or alternative approaches.
- Backend Developer Agent
  - Executes implementation tasks assigned by the Tech Lead and reports results.
  - Writes backend production code and test code, referencing `CLAUDE.md`, `.claude/skills/*`, and design documents.
  - **Must use the `sonnet` model.**
- QA Developer Agent
  - Inspects **implementation artifacts (code, tests)** completed by Backend Developer agents against the full set of use cases.
  - Thoroughly checks business edge cases, technical edge cases, unintended behavior, and missing validations.
  - **Verification target**: Whether the implemented code and tests correctly satisfy the requirements.
- Reviewer Agent
  - Critically evaluates the Tech Lead's **plan and decisions (design direction, task distribution, dependency ordering, etc.)** with evidence-based reasoning.
  - Prioritizes raising rational, dispassionate objections; surfaces problems and weak assumptions.
  - **Verification target**: Validity of the plan and design judgments made by the Tech Lead.

> **QA vs Reviewer distinction**: QA verifies implementation artifacts; Reviewer verifies decisions and design. The input targets are different.

## Agent Tool Parameters

See [`reference-agent-parameters.md`](reference-agent-parameters.md) for the full parameter table and notes per role.

## Procedure

1. Analyze the user request and define the required deliverables.
2. Tech Lead agent reviews related documents.
   - Priority targets: `CLAUDE.md`, `.claude/skills/*`, `docs/design/*`, `README.md`, `round*-docs/*`
   - Read additional documents only within the scope directly relevant to the task.
3. Tech Lead agent creates an execution plan.
   - Task units
   - Dependency relationships
   - Serial/parallel execution decisions
   - Required roles and headcount
   - Inputs and expected outputs for each agent
4. **Invoke Reviewer agent to review the plan.**
   - Reviewer agent points out validity issues, gaps, and weak assumptions with supporting evidence.
   - **The Tech Lead does not unconditionally accept the review results.** It evaluates the full context (user requirements, technical constraints, project rules) and makes a rational, dispassionate judgment on whether to accept or reject each opinion.
   - Accepted opinions are incorporated into the plan; rejected opinions must include the rejection rationale.
5. Tech Lead agent invokes agents according to the (review-adjusted) plan.
   - **Serial/parallel decision criteria**:
     - **Top priority rule**: Before parallel placement, the Tech Lead must assess **the possibility of conflicts between tasks**. If any form of conflict is expected — same file modifications, mutual dependencies within the same package, merge conflicts, etc. — tasks must be placed **in serial unconditionally**. Parallel placement is only permitted when the Tech Lead determines there will be no conflicts.
     - Tasks that require direct cross-BC references → **serial** placement
     - Independent tasks with no direct cross-BC references → **parallel** placement after confirming no conflicts
     - Tasks within the same BC with dependency relationships → serial placement
6. Each agent performs only its assigned work and reports results.
7. Tech Lead agent decides the next tasks based on completed results.
8. Once all required tasks are complete, Tech Lead agent summarizes the final state.

## Failure Handling

- When an agent task fails or produces insufficient results, **the Tech Lead decides entirely**.
- The Tech Lead analyzes the failure cause and autonomously decides on retry, reassignment to another agent, or applying an alternative approach.
- The failure handling process and decision rationale must be included in progress reports.

## Rules

- The Tech Lead agent must never write code directly.
- The Tech Lead agent must never write or modify tests directly.
- The Tech Lead agent must never perform QA inspection or review roles directly.
- The Tech Lead agent's role is limited to: planning, assignment, sequencing, result collection, next-step decisions, and failure handling.
- A subordinate role may have 0 members, but the Tech Lead must not take over that role's responsibilities even if it is empty.
- Backend Developer agents must report modified files, key changes, and verification results alongside their implementation output.
- QA Developer agents must separately report verified use cases, edge cases, discovered issues, and remaining risks.
- Reviewer agents must prioritize criticism over simple agreement and must always provide evidence.
- Reviewer agents are invoked **immediately after plan creation**. After the implementation phase, QA agents take over.
- The Tech Lead **does not unconditionally accept** Reviewer results; it considers the full context and makes a rational, dispassionate judgment on acceptance.
- Implementation agents must not be invoked before document exploration and planning are complete.
- Progress reports to the user should be output frequently at the individual task completion level, not at major phase boundaries.

## Resource Usage Guide

- `CLAUDE.md`: Global development rules and architecture principles for the repository
- `.claude/skills/*`: Role-specific detailed rules for implementation, testing, review, comment style, etc.
- `docs/design/*`: Official design references
- `round*-docs/*`: Past design decisions, review records, implementation plans

## Validation

See [`reference-validation.md`](reference-validation.md) for the full validation checklist.

## Reporting

See [`reference-reporting.md`](reference-reporting.md) for the progress report format, examples, and output format per phase.
