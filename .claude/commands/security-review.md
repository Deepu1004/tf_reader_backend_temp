---
description: Security review of a branch against this project's ten real risks.
argument-hint: <branch-name>
allowed-tools: Read, Grep, Glob, Bash(git diff:*), Bash(git log:*), Task
---

Security review of branch `$1`. **Read only. Change nothing.**

Delegate the analysis to the `security-reviewer` subagent, so its reasoning stays in its own context
window and only the findings come back. Pass it:

- the branch name `$1`
- the output of `git diff main...$1 --stat`, so it knows the scope before it starts

Then report what it returns, unchanged apart from formatting. **Do not add findings of your own** and
do not soften the ones it gives you.

## Why this is a subagent and not just a prompt

Two reasons. It keeps a long security analysis out of the main session, which is cheaper and leaves
room for the actual work. And it makes the checklist a file that gets reviewed and improved, rather
than a paragraph that drifts every time somebody runs it.

## What this review is not

It is not a generic OWASP pass. Generic security review on a prototype produces mostly noise, and
noise trains people to skip reviews. The subagent works from ten specific risks that are real in
**this** system, in `.claude/agents/security-reviewer.md`.

If the diff touches none of those ten areas, the right answer is short: name the areas it does touch,
say the ten do not apply, and stop. **A one-line security review is a valid security review.**

## Escalation

If a finding is in the first three checks, which are the ones that can leak a key or grant access
that was not bought, say so in the first line of the output rather than burying it in a list. Those
three are worth interrupting somebody for. The other seven are not.
