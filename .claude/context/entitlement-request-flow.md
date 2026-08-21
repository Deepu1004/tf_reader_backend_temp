# Institution book/collection requests: the flow

**Status: proposed, not yet built.** This is the agreed shape of the flow, for anyone
reviewing it before implementation starts.

## In one paragraph

Institutions don't get to grant themselves access to books. Instead, they browse the full
catalogue of everything every publisher has, and *request* what they want. A super admin
reviews the request and approves or rejects it. No payment step for now — approval is the
only gate.

## The flow, step by step

1. **Onboarding (already built, unchanged).** A super admin creates institutions and
   publishers. Institutions and publishers never onboard themselves.
2. **Publishing content (already built, unchanged).** A publisher (or super admin) creates
   catalogue items and collections. Institutions never create or edit catalogue content —
   that stays publisher/super-admin territory.
3. **Browse & request (new).** An institution admin opens a screen that lists every catalogue
   item and collection from every publisher, each tagged with the institution's current
   status for it: **none**, **pending**, **active**, or **revoked**. This is one screen for
   both "things I could ask for" and "things I already have" — nothing hidden, nothing
   duplicated.
4. **Requesting (new).** The institution admin picks an item, collection, or a whole
   publisher and requests access. This creates a **pending** entitlement — a real record,
   but one that grants no access yet.
5. **Approval (new).** A super admin sees the pending requests and approves or rejects each
   one. Approve turns it into an active entitlement (access is live). Reject closes it out as
   revoked (no access, request is done).
6. **Institution sees the outcome.** The same browse screen from step 3 now shows the item as
   active (or back to none/rejected), so there's no separate "check your request status"
   page.

## What changes technically

Nothing new is invented here — the existing `Entitlement` record already models
"institution has access to X." A request is simply an `Entitlement` that starts in a new,
not-yet-active state.

- **`EntitlementStatus`** (`com.tf.reader.catalogue.entity.EntitlementStatus`) gains one new
  value: `PENDING`, alongside the existing `ACTIVE`, `SUSPENDED`, `REVOKED`.
- **`EntitlementAdminService.create(...)`**: when the caller is an `INSTITUTION_ADMIN`, the
  new entitlement is always forced to `PENDING`, regardless of what status was requested — an
  institution can never grant itself live access directly. When the caller is `SUPER_ADMIN`,
  `ACTIVE` is still allowed directly, so a super admin can hand-grant access without going
  through the request dance if there's a reason to.
- **Approve / reject**: a status-change path on the existing entitlement, restricted to
  `SUPER_ADMIN` only via `AdminScopeAuthorizer.requireSuperAdmin()`. Approve transitions
  `PENDING → ACTIVE`; reject transitions `PENDING → REVOKED`. No new terminal state is needed
  for "rejected" — revoked already means "no access," which is exactly what a rejected
  request should mean.
- **A new read path for the browse screen**: institutions need to see the full catalogue
  (not filtered by what they already have) plus their own entitlement status per row. This is
  likely an extension of the existing `GET /api/admin/v1/catalogue-items` (and/or
  collections) listing rather than a new endpoint from scratch — the exact response shape is
  left to a follow-up implementation plan.
- **No changes** to `Institution`, `Publisher`, `CatalogueItem`, or `BookCollection` entities.
  No new MongoDB collection — this reuses the entitlements collection that already exists.

## What does NOT change

- Institutions still cannot create or edit catalogue items, collections, publishers, or other
  institutions.
- Onboarding institutions and publishers stays super-admin-only.
- No payment integration of any kind.

## Open questions, deliberately left for the implementation plan

- Exact response shape for "browse the catalogue with my entitlement status attached."
- Whether a rejected (`REVOKED`) request can be re-requested later, and whether there's any
  cooldown before it can be.
- Whether the institution gets any notification when a request is approved or rejected, or
  whether checking the browse screen again is enough for a prototype.
