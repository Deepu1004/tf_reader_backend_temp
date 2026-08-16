---
description: Technical review of a branch against the contract and the style guide. Finds nothing it cannot point at.
argument-hint: <branch-name>
allowed-tools: Read, Grep, Glob, Bash(git diff:*), Bash(git log:*), Bash(git show:*), Bash(./mvnw:*), Write(.reviews/**)
---

Technical review of branch `$1`. **Read only. Do not fix anything**, even something trivial. The
author fixes their own code; that is how they learn it.

This is the review for **the person who wrote the code**, so technical language is fine. For the
plain-language version aimed at Ashwin, that is `/pr-review`.

## Read

1. `.claude/STYLE.md`, in full. Most findings will come from here.
2. `.claude/context/api-contract-digest.md`, if a controller or DTO changed.
3. `git diff main...$1`
4. The full text of any file the diff changes substantially, since a diff hides context.

## Check, in this order

**1. Correctness against the contract.** For every endpoint touched: does the path, method, status
code, required field and error code match the digest? A mismatch here is the most expensive kind of
bug, because another team is coding against the contract.

**2. The style guide.** Go through `STYLE.md` as a list. Name the rule that is broken, not just "this
is complex". Specifically look for the things that guide bans: an interface with one implementation,
a builder for 3 fields, a new custom exception, a new error envelope, `@Data`, field injection, a
method over 30 lines, a class over 150.

**3. Reuse that was missed.** Did they write something that already exists in `common/` or in the
module's own `service/`? This is the most common finding on this project and the easiest to fix.

**4. Module boundaries.** Does any import cross into another module's `entity/`, `repository/` or
`service/`? Only `api/` packages may be imported across modules. This is the rule no review catches
reliably, so check the import block of every changed file deliberately.

**5. Tests.** Is there one happy path and one failure path per new service method? Not coverage
percentage, just those two. A new endpoint with no test is a finding.

**6. Does it build.** Run `./mvnw -q test` if the diff touches Java. If you cannot run it, say so
explicitly rather than implying it passed.

## Output

Print to the session, and also write `.reviews/$1--code--<YYYY-MM-DD>.md`.

Group by severity, and within a group put the cheapest fix first.

```
## Must fix
- path/File.java:NN  <one line: what is wrong>
    why: <one line: what breaks>

## Should fix
- path/File.java:NN  <one line>

## Style guide
- path/File.java:NN  breaks "<the exact rule from STYLE.md>"

## Verified myself
- <what you actually ran, and the result>
- <what you could NOT run, and why>
```

**The last section is not optional.** Say what you ran and what you could not. A review that implies
it ran tests it did not run is worse than no review. If Docker is unavailable and the Testcontainers
tests could not run, that sentence belongs in the output.

If there is nothing in a group, delete the heading rather than writing "none".
