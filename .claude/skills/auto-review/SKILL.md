---
name: nsp-council
description: |
  NSP FE Multi-AI Council v4.0 planning system.
  Based on "Adversarial Planning, Deterministic Execution" philosophy.
  Generates high-quality implementation specs (SPEC.md) through 3-round competitive debate
  between Claude (Chair) + Codex (Architect) + Gemini (Challenger).

  v4.0 new: Adaptive convergence detection, Jury independent verification, RedTeam 3-defect minimum

  Use when:

  - "Planning", "implementation plan", "design review" requests
  - "council", "multi-agent" keywords mentioned
  - New feature/component/page design needed
  - Complex refactoring or architecture decisions needed
---

# NSP Council v4.0: Multi-AI Planning System

> **Core Philosophy**: Adversarial Planning, Deterministic Execution — raise plan quality through adversarial debate, then implement mechanically.

## v4.0 Change Summary

| Item | v3.0 | v4.0 |
|------|------|------|
| Debate structure | 2 rounds (fixed) | **3 rounds (adaptive)** |
| RedTeam requirement | 1+ defects | **3+ defects mandatory** |
| Termination condition | Fixed rounds | **Convergence detection early exit** |
| Final verification | Chair only | **Jury independent verification added** |

Research basis: [SWE-Debate](https://arxiv.org/abs/2507.23348), [D3 Framework](https://arxiv.org/abs/2410.04663), [Adaptive Stability](https://arxiv.org/html/2510.12697v1)

---

## Role Definitions (5 Personas)

| Persona | Model | Role |
|---------|-------|------|
| **The Chair** | Claude | Moderator, GoT management, convergence determination |
| **The Architect** | Codex | Conservative expert, follows existing patterns |
| **The RedTeam** | Gemini | Radical challenger, **3 defects mandatory** |
| **The Scribe** | Claude | SPEC.md authoring (no code writing) |
| **The Jury** | Claude | Independent verification in **new session** |

---

## Workflow Overview

```
Phase 0: Interview (requirements gathering)
    |
Phase 1: The Council (3-round adaptive debate)
    |— Round 1: Competitive Localization
    |   Codex: Location analysis -> Gemini: Location challenge -> Chair: Scope confirmation
    |
    |— Round 2: Design Debate
    |   Codex: Plan A -> Gemini: RedTeam 3-defect attack
    |   -> Codex: Rebuttal + Plan A' -> Chair: GoT branching
    |
    |— Round 3: Final Challenge (skip if converged)
    |   Gemini: Re-attack Plan A' -> Chair: Convergence determination
    |
    +— Decide: Chair GoT merge -> Scribe SPEC.md -> Jury verification
    |
Phase 2: The Factory (implementation based on SPEC.md) - separate execution
```

---

## Phase 0: Requirements Interview

Gather sufficient context via AskUserQuestion. Never assume.

Required checks: scope, UI/UX, state management, existing patterns, priorities, constraints.

Detailed checklist: [references/council-checklist.md](references/council-checklist.md)

---

## Phase 1: The Council

> No code is written in this phase. Focus exclusively on SPEC.md creation.

### Round 1: Competitive Localization

**Goal**: Where should we fix/build?

1. Chair distributes requirements + instructs codebase exploration
2. Codex location analysis [call 1/3] — stability perspective
3. Gemini location challenge [call 1/3] — innovation perspective
4. Chair analyzes both sides and confirms modification scope

### Round 2: Design Debate

**Goal**: How should we build it?

1. Codex presents Plan A [call 2/3]
2. Gemini RedTeam attack [call 2/3] — **3+ defects mandatory** (1+ each for concurrency/security/performance)
3. Codex rebuttal + Plan A' revision [call 3/3]
4. Chair manages GoT branches (Branch A, A', B)

### Round 3: Final Challenge (Skip If Converged)

**Convergence detection**: Skip if no new defects found in Round 2.

1. Gemini re-attacks Plan A' [call 3/3 - conditional]
2. Chair convergence determination (CONVERGED / CONTINUE)

> Accuracy may decline beyond 3 rounds of debate. No additional rounds after Round 3.

### Decide: Jury Verdict

1. Chair: GoT merge (Branch A + A' + B → unified design)
2. Scribe: SPEC.md draft — template: [references/spec-template.md](references/spec-template.md)
3. Jury: Independent verification in new session — protocol: [references/jury-protocol.md](references/jury-protocol.md)

Detailed prompts and GoT branching/merging examples: [references/council-workflow.md](references/council-workflow.md)

---

## Phase 2: The Factory

Separate execution. Enter after SPEC.md + Jury approval. Do not implement anything not in SPEC.md.

AgentPrune applied: Context Sliding, Role-Based Filtering, Silence is Gold.

Details: [references/council-workflow.md](references/council-workflow.md) (Phase 2 section at bottom)

---

## SPEC.md Core Structure

```
1. Changed files list (CREATE/MODIFY/DELETE)
2. Function signatures (input/output types)
3. Component structure (file tree)
4. Libraries/patterns used
5. Don't Do List
6. Test checklist
7. Council debate summary (3 defects + responses + Jury verdict)
```

Full template: [references/spec-template.md](references/spec-template.md)

---

## Call Count Summary

| Role | Round 1 | Round 2 | Round 3 | Decide | Total |
|------|---------|---------|---------|--------|-------|
| **Codex** | 1 | 2 | - | - | **3** |
| **Gemini** | 1 | 1 | 1 (conditional) | - | **2-3** |
| **Jury** | - | - | - | 1 | **1** |

Total external calls: minimum 6, maximum 7

---

## CLI Command Summary

```bash
# Round 1
codex exec "[location analysis prompt]" 2>&1
gemini "[location challenge prompt]" --yolo 2>&1

# Round 2
codex exec "[Plan A prompt]" 2>&1
gemini "[RedTeam 3-defect attack prompt]" --yolo 2>&1
codex exec "[rebuttal + Plan A' prompt]" 2>&1

# Round 3 (only when not converged)
gemini "[Plan A' re-attack prompt]" --yolo 2>&1

# Save SPEC.md
mcp__serena__write_memory("spec-[feature-name]", SPEC_content)
```

---

## References Index

| File | Contents |
|------|----------|
| [council-workflow.md](references/council-workflow.md) | Round 1/2/3 detailed prompts, GoT branching/merging, convergence determination, Phase 2 |
| [jury-protocol.md](references/jury-protocol.md) | Jury verification checklist, invocation method, judgment criteria |
| [council-checklist.md](references/council-checklist.md) | All checklists for Phase 0/1/2 combined |
| [spec-template.md](references/spec-template.md) | Full SPEC.md template |
| [plan-template.md](references/plan-template.md) | Debate record template |
| [nsp-fe-patterns.md](references/nsp-fe-patterns.md) | NSP FE code pattern guide |

---

## NSP FE Reference Documents

- Project root: `/CLAUDE.md`
- Pattern guide: [references/nsp-fe-patterns.md](references/nsp-fe-patterns.md)
- Schedule domain: `/src/domains/schedule/CLAUDE.md`
- Test guide: `/docs/test/CLAUDE.md`
