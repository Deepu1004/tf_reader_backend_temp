<!--
Four fields. Keep each answer short.
This is what /pr-review reads, so a vague description produces a vague review.
-->

## What
<!-- One or two sentences. What changed, not how. -->


## Why
<!-- The reason. If it implements a ticket or a decision, link or name it. -->


## How I tested it
<!-- What you actually ran. "./mvnw test, 18 passed" beats "tested locally".
     If you could not run something, say so and why. That is not a failing. -->


## Contract impact
<!-- Tick one.
     Anything other than "None" needs a heads up to the other teams before merge. -->

- [ ] None. No endpoint, DTO or error code changed
- [ ] Additive only. New endpoint or new optional field, nothing existing altered
- [ ] **Breaking.** An existing field, path, status code or error code changed

<!-- If breaking, name what and who is affected: -->


---

<!-- Before requesting review:
     [ ] ./mvnw test passes, or you have said why it does not
     [ ] no key, token, password or .env in the diff
     [ ] the branch is named <firstname>/<team>/<feature>
     [ ] no Co-Authored-By: Claude anywhere in the history
-->
