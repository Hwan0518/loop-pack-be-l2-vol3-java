---
name: requirements-analysis
description:
  Analyzes provided requirements and clarifies ambiguous ones through Q&A with the developer.
  Once all clarification is complete, creates sequence diagrams, class diagrams, ERDs, etc. in Mermaid syntax.
  Use when requirements are provided and need to be clarified before writing code.
---

Always follow this flow when analyzing requirements.

### 1. Do not take requirements at face value — restate them as problem situations.

- Do not stop at just organizing requirement statements.
- Reinterpret as "What problem exists now, and why are we trying to solve it?" rather than "What should we build?"
- Separate and organize from these perspectives:
    - User perspective
    - Business perspective
    - System perspective

> Example
> "Cancel payment when order fails" → "The problem of maintaining consistency so that payment success/failure and order status don't become misaligned"

### 2. Do not hide ambiguous requirements — surface them explicitly

- Do not guess or decide on your own.
- Explicitly list undecided parts from the requirements.
  **The following types of questions must be included:**
- Policy questions: reference time points, success/failure conditions, exception handling rules
- Boundary questions: where does one responsibility end, where is the separation
- Extension questions: is there a possibility of future changes

### 3. Present questions for requirement clarification in a form easy for the developer to answer

- Questions have priorities (most important first).
- When options exist, present them with options + impact assessment.

> Format example:

- Option A: Process in a single transaction → Simple implementation, low scalability
- Option B: Separate into stages → Complex structure, better for extension/compensation handling

### 4. Based on agreed content, establish the conceptual model first

- Do not jump straight into code or technical discussions.
- First define:
    - Actors (users, external systems)
    - Core domain
    - Supporting/external systems
- The purpose of this step is design thinking alignment, not "implementation."

### 5. Always present diagrams in the order: reasoning → diagram → interpretation

**Always explain before drawing a diagram**

- Why this diagram is needed
- What this diagram aims to verify

**Diagrams are written in Mermaid syntax**
Usage criteria:

- **Sequence diagram**
    - Responsibility separation
    - Call order
    - Transaction boundary verification
- **Class diagram**
    - Domain responsibilities
    - Dependency direction
    - Cohesion verification
- **ERD**
    - Persistence structure
    - Relationship ownership
    - Normalization status

### 6. Do not just throw diagrams out — explain how to read them

- Explain "key points to look for in this structure" in 2-3 lines.
- Attach interpretation so design intent is visible.

### 7. Always mention potential risks of the design

- Do not hide risks the current design may have.
    - Transaction bloat
    - Increased coupling between domains
    - Expanded impact scope on policy changes
- Do not present solutions as definitive answers — present them as options.

### Tone & Style Guide

- Maintain a design review tone, not a lecture tone
- Rather than presenting as the definitive answer, provide alternative options when available
- Treat intent, responsibilities, and boundaries as more important than code
- Focus on drawing out what needs to be thought about before implementation
