---
description: First-session setup for a new team member. Run this once.
allowed-tools: Read, Bash(git config:*), Bash(git rev-parse:*), Bash(./mvnw:*), Bash(ls:*), Bash(cat:*), Write(.claude/team)
---

Set this clone up for a new team member. Work through it with them, one step at a time, and confirm
each step before moving on.

## 1. Git identity

Show them what is currently set:

```
git config user.name
git config user.email
```

**If either is empty, print the two commands and let them run them.** Do not run them yourself: the
identity has to genuinely be theirs, and a hook blocks you from setting it anyway.

Explain why in one sentence: every commit on this project is attributed to a person, never to Claude,
and a git hook rejects the commit otherwise.

## 2. Team

Ask which team they are on, then write it:

```
echo wokay > .claude/team
```

Explain that the file is gitignored, so it is theirs alone and never conflicts with anybody.

## 3. Activate the git hooks

```
git config core.hooksPath .githooks
```

Explain what the two hooks do, briefly: one formats the code so formatting never becomes a review
comment, the other rejects a commit that is not attributed to a human.

## 4. Make the working folders

```
mkdir -p .contrib .reviews
```

## 5. Check the build actually runs

```
./mvnw -q -DskipTests package
```

If it fails, that is the first thing to fix and nothing else matters until it does. Say what failed
rather than working around it.

## 6. Read three things, in this order

Point them at these and say why each one exists:

| File | Why |
|---|---|
| `CLAUDE.md` | the four hard rules and the module map. Ten lines of it matter most |
| `.claude/STYLE.md` | what not to write. It exists because the default is too much abstraction |
| `.claude/context/<their team>.md` | what their team owns, and the five things that bite |

## 7. Tell them the commands

| Command | When |
|---|---|
| `/ship` | every time they want to commit and open a PR |
| `/pr-review <branch>` | to understand somebody else's branch in plain words |
| `/code-review <branch>` | before opening their own PR |
| `/security-review <branch>` | if the branch touches keys, auth or an admin route |
| `/context` | if the context files changed mid-session |

## 8. Two habits, said once

- **Start a new session per feature.** A long session gets expensive and less accurate.
- **Name the files that matter** instead of asking Claude to find them. It is faster and cheaper.

Finish with a one-line summary of what was set and what is still outstanding.
