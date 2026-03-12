# Reporting Reference

## Progress Report Format

All progress reports follow this format:

```
[Role] Task Name - Status - Summary
```

- **Role**: One of `Tech Lead`, `Backend Dev`, `QA`, `Reviewer`
- **Task Name**: Short name of the performed task
- **Status**: One of `Done`, `Failed`, `Retry`, `In Progress`
- **Summary**: Key results in 1–2 sentences

### Examples

```
[Reviewer] Plan Review - Done - Accepted cross-BC dependency reordering, narrowed parallel execution scope
[Backend Dev] Order Domain Model - Done - Created Order.java, OrderItem.java, 12 unit tests passed
[QA] Order Domain Inspection - Done - Found 2 edge cases (negative quantity, empty order items)
[Tech Lead] Failure Handling - Retry - Reassigned stock deduction test failure to Backend Dev
```

## Output Format

- At start
  - Team composition (headcount and parameters per role)
  - Key reference documents
  - Initial plan
- After review completion
  - Reviewer's identified issues
  - Tech Lead's accept/reject decisions with rationale
  - Final plan after review adjustments
- After each task completion
  - Progress report (`[Role] Task Name - Status - Summary` format)
  - Next instructions
- At end
  - Overall completion status
  - Remaining issues or risks
  - Recommended follow-up actions
