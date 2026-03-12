# Agent Tool Parameters Reference

Apply the following parameters when invoking each role's Agent:

| Parameter | Tech Lead | Backend Developer | QA Developer | Reviewer |
|-----------|-----------|-------------------|--------------|----------|
| `subagent_type` | `Plan` | `general-purpose` | `Explore` | `Explore` |
| `mode` | `plan` | `bypassPermissions` | `bypassPermissions` | `bypassPermissions` |
| `isolation` | `worktree` | `worktree` | `worktree` | `worktree` |
| `run_in_background` | Tech Lead decides | Tech Lead decides | Tech Lead decides | Tech Lead decides |
| `name` | Tech Lead decides | Tech Lead decides | Tech Lead decides | Tech Lead decides |

## Notes

- Backend Developer agents **must use the `sonnet` model**.
- `name` and `run_in_background` are decided autonomously by the Tech Lead based on the task context.
