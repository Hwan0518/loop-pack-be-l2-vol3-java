---
name: commit-protocol
description: Git commit message conventions, PR splitting strategy, and pre/post-commit verification procedures. Use when committing code changes.
---

# Commit & PR Protocol

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

## 8. PR Splitting Strategy

### 8.1 Core Principle

**리뷰 가능한 단위로 PR을 분리한다.** 클래스 분리는 설계 원칙이므로 파일 수 자체를 줄이지 않는다. 대신 하나의 PR이 다루는 관심사를 좁혀 리뷰어가 맥락을 유지할 수 있게 한다.

### 8.2 분리 기준

| 기준 | 설명 | 예시 |
|------|------|------|
| **구조 vs 기능** | 아키텍처 리팩토링과 비즈니스 로직 변경을 분리 | PR1: CQRS 구조 도입 / PR2: 쿠폰 발급 기능 |
| **레이어별** | 한 레이어의 변경이 다른 레이어에 영향 없으면 분리 가능 | PR1: Domain 모델 + Repository / PR2: Application + Interfaces |
| **도메인별** | 독립적인 BC나 도메인은 별도 PR로 | PR1: Catalog BC / PR2: Ordering BC |
| **횡단 관심사** | 인증, 에러 처리, 공통 설정 등은 별도 PR로 선행 | PR1: 인증 구조 변경 / PR2: 각 도메인 적용 |

### 8.3 PR 규모 가이드

| 규모 | 파일 수 (대략) | 리뷰 난이도 |
|------|:-------------:|:-----------:|
| Small | ~30 | 집중 리뷰 가능 |
| Medium | 30~60 | 구조 파악 후 리뷰 가능 |
| Large | 60~100 | PR 설명 필수, 분리 검토 |
| **분리 필요** | **100+** | **반드시 분리** |

### 8.4 순차적 PR 의존성

의존 관계가 있는 PR은 순서를 명시한다:

```
PR #1: [base] 공통 인프라 변경 (인증 구조)        ← 먼저 머지
PR #2: [depends on #1] Cart/Like 도메인 적용      ← #1 머지 후
PR #3: [depends on #1] Order/Coupon 도메인 적용   ← #1 머지 후 (#2와 독립)
```

- PR 본문에 의존 PR 번호를 명시한다
- 선행 PR이 머지되기 전에 후행 PR을 올릴 수 있으나, base branch를 선행 PR branch로 설정한다

### 8.5 커밋 단위

- 각 커밋은 **빌드와 테스트가 통과하는 상태**를 유지한다
- 하나의 PR 내에서 커밋은 논리적 작업 단위로 구분한다 (파일 단위 X)
- 예: `feat: CartItem 도메인 모델 및 Repository 구현` → `test: CartItem 단위 테스트 추가`
