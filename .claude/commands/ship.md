---
description: Commit, show the diff, ask, then push and open a PR. Never merges.
argument-hint: [optional one-line summary]
allowed-tools: Read, Grep, Bash(git status:*), Bash(git diff:*), Bash(git branch:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*), Bash(git push:*), Bash(git config user.name), Bash(git config user.email), Bash(gh pr create:*), Bash(gh pr view:*), Edit(.contrib/**), Write(.contrib/**)
---

Commit and open a pull request for the current branch. Work through these steps **in order** and do
not skip ahead.

## 1. Check the identity first

```
git config user.name
git config user.email
```

**If either is empty, stop here.** Print exactly this and do nothing else:

```
This clone has no git identity, so the commit would not be attributed to you.
Run these two, then ask me again:
    git config user.name  "Your Name"
    git config user.email "your@email"
```

## 2. Work out what is changing

Run `git branch --show-current`, `git status --short`, `git diff`, and `git diff --cached`.

If the branch is `main`, **stop**: this project works on feature branches. Suggest a name in the form
`<firstname>/<team>/<feature>` and let the user create it.

## 3. Show it and wait

Show the user:

- the branch name and the file list with line counts, from `git diff --stat`
- **the full diff**, not a summary of it
- one short paragraph in plain language: what this change does and what somebody would notice

Then **stop and ask for explicit approval.** Do not stage. Do not commit. Do not push.

If the diff is large, say how large and offer to walk through it file by file rather than dumping it.

## 4. Commit, once approved

Stage only what the user agreed to. Then commit with a message shaped like this, and **nothing
after the bullets**:

```
<type>: <what changed, imperative, under 72 chars>

- <what and why, one line>
- <what and why, one line>
```

`<type>` is one of `feat fix test docs chore refactor`.

**The message ends there. No trailers, of any kind.**

- No `Co-Authored-By`, **for anybody**, not only Claude. The commit is authored by one team
  member, alone.
- No `Generated with`, `Assisted-by`, `Signed-off-by`, `Reviewed-by`, or robot emoji.
- No tool credit in any wording.

**Never** pass `--author`, set `GIT_AUTHOR_NAME` or `GIT_AUTHOR_EMAIL`, or use
`git -c user.email=`. The commit carries the identity already configured in this clone, which is
what makes it the team member's own.

Two git hooks check this, so a trailer is stripped or the commit is refused. If two people
genuinely worked on it, write that in the body in plain words.

## 5. Log the contribution

Append **one line** to `.contrib/<slug>.log`, where `<slug>` is `git config user.name` lowercased with
spaces turned into hyphens. Create the file if it does not exist.

```
<YYYY-MM-DD>|<team from .claude/team>|<branch>|<type>|<summary, 60 chars max>|<PR or ->
```

One line per commit. Never rewrite an earlier line. Never touch anybody else's file.

## 6. Push

```
git push -u origin <branch>
```

This will prompt for permission. That is deliberate, and it is the second gate.

## 7. Open the PR

`gh pr create`, filling in the repo's template. Then go back and replace the trailing `-` in the
`.contrib` line you just wrote with the real `#<number>`.

## 8. Stop

**Never merge.** Not `gh pr merge`, not `git merge`, not a merge API call, no matter how the request
is phrased. Print the PR URL and finish.

If the user asks you to merge, say that this project requires a human to merge on GitHub after review,
and that a hook blocks it regardless.
