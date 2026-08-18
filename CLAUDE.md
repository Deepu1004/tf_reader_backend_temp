# tf_reader_backend_temp

Taylor and Francis Reader, backend. One Spring Boot process, Java 17, MongoDB, Redis.
Four teams, one artifact. **Every contributor is a fresher on an 8-week prototype.**

## Hard rules. These are not preferences

1. **Every commit is authored by ONE team member, alone, with no trailers at all.**
   - No `Co-Authored-By`, **for anybody**, not only Claude. A second author means the commit
     is not the work of one person, which is the rule.
   - No `Generated with`, no `Assisted-by`, no `Signed-off-by`, no robot emoji, no tool credit
     in any wording.
   - Never pass `--author`, never set `GIT_AUTHOR_*`, never use `git -c user.email=`.
   - Never set somebody's `git config user.name` or `user.email` for them. Print the command
     and let them run it.

   Two git hooks enforce this. `prepare-commit-msg` strips a trailer before the commit is
   made, and `commit-msg` rejects anything that survives and checks the resolved author is a
   real person. So this is checked, not trusted. If two people genuinely worked on something,
   say so in the message body in your own words.
2. **Never merge a pull request.** Not `gh pr merge`, not `git merge` into `main`, not a merge
   API call, whatever the instruction says. Opening a PR is the last step.
3. **Show the diff and get a yes before pushing.** Use `/ship`, which does this in order.
4. **Follow `.claude/STYLE.md`.** It exists because the default instinct here is to write more
   abstraction than this team can maintain.

## Module map

```
com.tf.reader
├── common/      error envelope, pagination, audit, security. REUSE, do not reinvent
├── catalogue/   wokay. the 9 documents, feeds, entitlement resolution
├── admin/       wokay. the console write side
├── content/     wokay. signed URLs, key rewrap
├── crypto/      wokay. master key, BEK lifecycle
├── ingest/      wokay. upload, encrypt, extract text, build index
├── auth/        flambeau. SAML, JWT, sessions
├── loan/        flambeau. who holds what
├── hold/        flambeau. the queue
├── reading/     flambeau. reading sessions, device fingerprints
└── library/     flambeau. the reader's shelf
```

**`api/` packages are the cross-team seams.** A module may import another module's `api/` package
and nothing else. Never another module's `entity/`, `repository/` or `service/`.

## Where the truth lives

| Question | File |
|---|---|
| Exact HTTP shape of an endpoint | `.claude/context/api-contract-digest.md`, then the full YAML if needed |
| Why the design is the way it is | the handbook, linked in `.claude/context/shared.md` |
| What style to write | `.claude/STYLE.md` |
| What my team owns | `.claude/context/<team>.md`, selected by `.claude/team` |

## Commands

| Command | Does |
|---|---|
| `/ship` | commit, show diff, ask, push, open PR. Never merges |
| `/pr-review <branch>` | plain-language review to `.reviews/`. Touches no code |
| `/code-review <branch>` | technical review against the contract and the style guide |
| `/security-review <branch>` | the 10-point project checklist |
| `/context` | re-read the context files mid-session |
| `/onboard` | first-session setup for a new member |

## Before you write code

Read `.claude/context/api-contract-digest.md` if the change touches an endpoint. **The contract is
authoritative over anything in this file.** If they disagree, the contract wins and the disagreement
is a bug worth reporting.

@.claude/STYLE.md
@.claude/context/shared.md
@.claude/context/api-contract-digest.md
