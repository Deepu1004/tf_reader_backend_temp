---
description: Review a branch in plain language. Writes a file. Never touches code.
argument-hint: <branch-name>
allowed-tools: Read, Grep, Glob, Bash(git diff:*), Bash(git log:*), Bash(git branch:*), Bash(git show:*), Bash(gh pr view:*), Bash(gh pr diff:*), Write(.reviews/**)
---

Review branch `$1` against `main`.

**You cannot modify code in this command and must not try.** The only path you may write to is
`.reviews/`.

## Read, in this order, and stop when you have enough

1. `.claude/context/shared.md` and `.claude/STYLE.md`
2. `.claude/context/api-contract-digest.md`, but only if the diff touches a controller, a DTO or an
   endpoint
3. `git diff main...$1 --stat`, then the diff itself
4. `git log main..$1 --format='%an  %s'`, so you know who wrote it and what they thought they did
5. `gh pr view $1` if a PR is open, for the description

Do **not** read the full `wokay-api.yaml` or the handbook. They are 147 KB and 194 KB.

## Write to `.reviews/$1--<YYYY-MM-DD>.md`

Replace `/` in the branch name with `-` so the filename is valid.

## How it must read

- **First person, as if Ashwin wrote it himself after reading the branch.** "I looked at", "I could
  not tell whether", "I would want". **Never "you", never "your changes"**, never address a reader.
- **30 to 40 lines of substance. 50 lines absolute maximum, including headings.** Count before you
  finish.
- **Plain language.** The reader does not read code well. If a technical term is unavoidable, define
  it in the same sentence in about four words.
- **No code block longer than 3 lines.** Point at `file:line` instead of pasting.
- No praise, no filler, no "great work". State what is there.

## Sections, in this order

```markdown
# <branch>, reviewed <date>

## What was done
- one line each, at most 8 bullets, plainest possible words

## What it actually does
6 to 10 lines. The point of the change in terms of what a reader of the app, or another
team, would actually notice. Not a restatement of the file list.

## Issues
Either a numbered list, most serious first, each with one line of what is wrong and one
line of why it matters. Or the single line: No problems found.

## Worth flagging
Up to 4 bullets: style guide violations, missing tests, anything that contradicts the API
contract. Omit this whole section if there is nothing.

Verdict: approve | approve with comments | needs changes
```

**Do not invent issues to fill space.** If the branch is clean, "No problems found." is the correct
and useful answer. A review that always finds three things is a review nobody trusts.

If something in the diff is genuinely unclear, say so in the Issues list as "I could not tell whether
X" rather than guessing. That is a real finding, not a failure.

Finally, tell the user the file path and the line count, so they know the budget was met.
