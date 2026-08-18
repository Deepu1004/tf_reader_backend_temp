# flambeau context

**PLACEHOLDER. Not written yet.**

This file exists so that adding flambeau later needs no redesign. When flambeau are ready, fill it in
and merge to `main`; it reaches the fork on the next weekly sync. Each flambeau member then runs:

```bash
echo flambeau > .claude/team
```

Nothing else changes. `.claude/team` is gitignored, so one team's setting never leaks to the other and
the file never conflicts on a merge.

---

## What to put here, mirroring `wokay.md`

| Section | Content |
|---|---|
| What we own | CAP-4 and CAP-6. Modules `auth/ loan/ hold/ reading/ library/` |
| Do not edit | `catalogue/ admin/ content/ crypto/ ingest/` are wokay's |
| Collections | flambeau's own, including `users`, `loans`, `licences`, `holds`, `deviceFingerprints` |
| Things that bite | the Redis lease and hold queue, token audiences, the weekly fork sync |
| Branch naming | `<firstname>/flambeau/<feature>` |

## Two things flambeau will need from us, already true today

**The two seams are published as code**, in `catalogue/api/` and `content/api/`. Import those
packages and nothing else from a wokay module.

```java
EntitlementDecision check(SubjectRef subject, String itemId);
ContentGrant       grant(ContentGrantRequest request);
```

**flambeau owns every content read, all three tiers.** wokay exposes no HTTP endpoint that returns a
book. Every acquisition link in every feed points at a flambeau path, supplied to us as configuration
in `tf.flambeau.base-url`. Three of our error codes surface through your endpoint and should use our
envelope rather than a second one: `NO_ENTITLEMENT`, `DOWNLOAD_NOT_PERMITTED`, `CONTENT_NOT_READY`.

## One open question to settle when this file is written

Admin logout now revokes the access token immediately, because every admin request re-checks its
`adminSessions` row. **The admin access token needs a session id claim for that to work**, and no
claim is defined yet. App tokens are unaffected and still carry no server lookup, which is what keeps
feed rendering fast. Confirm the claim name with wokay before building against it.

Read `.claude/context/shared.md` and `docs/FORK-SYNC.md` as well.
