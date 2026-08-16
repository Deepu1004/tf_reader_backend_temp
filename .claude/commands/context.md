---
description: Re-read the project context mid-session, after it changed.
allowed-tools: Read, Bash(cat:*), Bash(ls:*), Bash(git log:*), Bash(git pull:*)
---

Re-read the context files. Use this when somebody has changed the contract or the style guide while
this session was open, so the session is working from stale information.

## Read, in this order

1. `.claude/team` to find out which team this clone is set to.
2. `.claude/context/shared.md`
3. `.claude/context/<team>.md`
4. `.claude/context/api-contract-digest.md`
5. `.claude/STYLE.md`

## Then check whether the digest is stale

If `api-docs/wokay-api.yaml` is newer than `.claude/context/api-contract-digest.md`, say so and
recommend `./scripts/gen-api-digest.sh`. A stale digest is worse than no digest, because it looks
authoritative.

## Report back in about six lines

- which team is set
- the three tier values, to prove the context actually loaded
- whether the digest is current
- anything in the context that changed in the last day, from `git log -1 --format='%ar  %s' -- .claude/context/`

**Do not summarise the whole context back at the user.** They wrote it. Confirm it loaded and name
what changed.
