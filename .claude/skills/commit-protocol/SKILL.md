---
name: commit-protocol
description: Git commit message conventions and pre/post-commit verification procedures. Use when committing code changes.
---

# Commit Protocol

## 1. Pre-Commit Checklist

### 1.1 Status Check

```bash
git status          # Check modified file list (never use -uall flag)
git diff            # Check staged + unstaged changes
git log --oneline -5  # Check recent commit message style
```

### 1.2 Staging Rules

- Selectively `git add` only related files (specify by filename)
- Avoid `git add -A` or `git add .` — prevents accidental inclusion of sensitive files
- **Never commit** `.env`, `credentials.json`, or other secret files

### 1.3 Pre-Check

- [ ] All tests passing?
- [ ] Lint/format checks passing?
- [ ] Unnecessary debug code (println, console.log) removed?
- [ ] Unused imports removed?

## 2. Commit Message Format

### 2.1 Basic Format

```
{type}: {Korean description}

- {changed file/class 1}
- {changed file/class 2}
```

### 2.2 Type Conventions

| type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat: 회원가입 API 구현` |
| `fix` | Bug fix | `fix: 비밀번호 검증 로직 오류 수정` |
| `test` | Add/modify tests | `test: 회원가입 E2E 테스트 추가` |
| `refactor` | Refactoring (no behavior change) | `refactor: 도메인 서비스로 검증 로직 이동` |
| `docs` | Add/modify documentation | `docs: CLAUDE.md에 레이어 규칙 추가` |
| `chore` | Build config, dependency management | `chore: Spring Boot 3.4.4로 업그레이드` |
| `init` | Initial setup | `init: 프로젝트 초기 구조 생성` |

### 2.3 Message Writing Rules

- Title written in **Korean** (code/commands in English)
- Keep title under 50 characters
- Body lists changed files/classes with `-` list format
- Focus on "why" rather than "what" changed

## 3. Commit Execution

```bash
git commit -m "$(cat <<'EOF'
{type}: {Korean description}

- {change list}

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

## 4. Post-Commit Verification

```bash
git status          # Check for missed files
git log --oneline -3  # Verify commit message
```

- [ ] Does the commit message follow conventions?
- [ ] Were any unintended files included?
- [ ] Did the pre-commit hook pass?

## 5. Prohibited Actions

| Item | Reason |
|------|--------|
| `git push --force` | Risk of destroying remote history (only on explicit request) |
| `git commit --amend` | Risk of modifying previous commit (only on explicit request) |
| `--no-verify` | Never bypass pre-commit hooks |
| `git reset --hard` | Risk of losing work (only on explicit request) |
| Committing secrets | `.env`, certificates, API keys — absolutely prohibited |

## 6. When Pre-Commit Hook Fails

1. The commit **was not created** when the hook fails
2. Fix the failure cause
3. Re-stage changes (`git add`)
4. **Create a new commit** (do not use `--amend` — it would modify the previous commit)

## 7. Branch Rules

- No direct push to main branch (`main`/`master`)
- Work on feature branches, merge via PR
- Force push to main/master is **not recommended even after warning**
