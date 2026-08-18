# wokay context

You are working as a member of **team wokay**. Five people: Abhishek R Nair, Akshay V Athreya,
Ashwin Sudhakar, Kashish Prasad, Anusripriya S.

## What we own

**CAP-1 Onboarding and Admin**, and **CAP-5 OPDS Catalogue**. That makes us the system of record and
the catalogue source, so **the other three teams are all blocked on us**. Publish contracts early
even when the implementation behind them is a stub.

| Module | Ours | Holds |
|---|---|---|
| `catalogue/` | yes | the 9 documents, OPDS feeds, entitlement resolution, feed cache |
| `admin/` | yes | the console write side, `/api/admin/v1/**` |
| `content/` | yes | signed URLs, the `ContentAccessGrant` seam |
| `crypto/` | yes | master key, BEK generate, wrap, unwrap, rewrap |
| `ingest/` | yes | upload, encrypt whole file, extract text, build the search index |
| `common/` | shared | error envelope, pagination, audit, security. Change with care |

Anything under `auth/ loan/ hold/ reading/ library/` is **flambeau's**. Do not edit those files. If
something there blocks you, say so rather than fixing it.

## The nine collections

```
publishers  collections  catalogueItems  institutions  entitlements
feedSettings  adminUsers  auditLogs  adminSessions
```

Ids are prefixed strings, not ObjectIds, so a log line is readable: `pub_ col_ item_ inst_ ent_
adm_ sess_`.

Two design rules that will look wrong until you know why:

- **A collection carries no `itemIds` array.** Membership lives on the book, in
  `catalogueItems.collectionIds`, so the relationship exists once and cannot disagree with itself.
- **`itemCount` and `resolvedItemCount` are computed on read**, never stored, for the same reason.

## Things that bite

1. **`catalogueVersion`** on an institution is the feed cache key and the `ETag`. Seven kinds of
   write must bump it, and the one people forget is **ingest finishing**, because it happens in a
   background worker rather than a controller. Miss it and that institution is served a stale feed.
2. **Only `PUBLISHED` and `READY` books appear in a feed.** Both conditions, every time.
3. **A shelf holds up to 50 ids and is a presentation list, never an authorisation one.** Every id is
   re-checked on read against status, content state and the current entitlement.
4. **`masterWrappedBek` never leaves the server.** Only `wrappedBek`, wrapped to a device key, is
   returned. Two fields, two names, on purpose: they look identical in base64, and a text search
   cannot tell them apart.
5. **An inactive institution returns `404`, not `403`**, so its existence is not disclosed.

## Our HTTP surface, in one glance

| Group | Count | Token |
|---|---|---|
| Public institutions | 2 | none |
| OPDS institution feeds | 4 | app token |
| OPDS public feeds | 3 | none |
| Catalogue fetch, `items:batch` | 1 | app token |
| Admin, `/api/admin/v1/**` | 34 | admin token |

Full shapes are in `.claude/context/api-contract-digest.md`.

## Branch naming

`<firstname>/wokay/<feature>`, matching what is already in use: `kashish/wokay/data-model`,
`akshay/wokay/SeedData`.

## Current known gaps

Worth knowing so you do not rediscover them:

- `ArchitectureTest.java` exists and is **one line**. The two ArchUnit rules that enforce the `api/`
  seam are not written, and call sites are appearing now.
- `api-docs/wokay-api.yaml` in this repo is a hand copy of the contract and **has already drifted**
  from the source. Prefer the digest.
