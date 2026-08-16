#!/usr/bin/env bash
# Generates .claude/context/api-contract-digest.md from the OpenAPI contract.
#
# Why: the contract is ~147 KB. Loading it into a session costs more than the session.
# This produces ~90 lines that answer most questions, and points at the full file for the rest.
#
# Uses python3 with regex only, no PyYAML, so it runs anywhere with no install step.
#
# Run in CI too, and fail the build if the committed digest differs from the generated one.
# That makes drift impossible rather than merely discouraged.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SRC="${1:-api-docs/wokay-api.yaml}"
OUT=".claude/context/api-contract-digest.md"

if [ ! -f "$SRC" ]; then
  echo "Contract not found at $SRC" >&2
  echo "Pass the path as the first argument, or fix the default in this script." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"

python3 - "$SRC" "$OUT" <<'PY'
import re, sys, datetime

src_path, out_path = sys.argv[1], sys.argv[2]
src = open(src_path, encoding="utf-8").read()

# --- operations: path, method, stability, called-by ---------------------------
# Paths sit at 2 spaces, methods at 4, and x- fields at 6, in this file's style.
ops = []
path = None
for line in src.split("\n"):
    m = re.match(r"^  (/\S+):\s*$", line)
    if m:
        path = m.group(1); continue
    m = re.match(r"^    (get|post|put|patch|delete):\s*$", line)
    if m and path:
        ops.append({"path": path, "method": m.group(1).upper(),
                    "stability": "", "called_by": "", "summary": ""})
        continue
    if ops:
        for key, pat in (("stability", r"^      x-stability:\s*(\S+)"),
                         ("called_by", r"^      x-called-by:\s*\[([^\]]*)\]"),
                         ("summary",   r"^      summary:\s*(.+)$")):
            m = re.match(pat, line)
            if m and not ops[-1][key]:
                ops[-1][key] = m.group(1).strip()

# --- schemas -----------------------------------------------------------------
seg = src[src.find("\n  schemas:"):]
schemas = re.findall(r"^    ([A-Z][A-Za-z0-9]*):", seg, re.M)

# --- error codes -------------------------------------------------------------
m = re.search(r"ErrorCode:.*?enum:\n((?:\s+- [A-Z_]+\n)+)", src, re.S)
codes = re.findall(r"- ([A-Z_]+)", m.group(1)) if m else []

# --- enums worth knowing -----------------------------------------------------
wanted = ["AccessTier", "ItemStatus", "ContentState", "AdminRole",
          "EntitlementScope", "EntitlementStatus", "SortOrder", "ContentType", "Intent"]
enums = {}
for name in wanted:
    m = re.search(r"^    " + name + r":\n(?:.*\n)*?      enum: \[([^\]]+)\]", src, re.M)
    if m:
        enums[name] = ", ".join(v.strip() for v in m.group(1).split(","))

def group(p):
    if p.startswith("/api/admin"): return "Admin"
    if p.startswith("/opds/v1/public"): return "OPDS public"
    if p.startswith("/opds"): return "OPDS institution"
    if p.startswith("/api/v1/institutions"): return "Public institutions"
    return "App"

L = []
w = L.append
w("# API contract digest")
w("")
w("**GENERATED FILE. Do not edit.** Run `./scripts/gen-api-digest.sh` after the contract changes.")
w(f"Source `{src_path}`, generated {datetime.date.today().isoformat()}.")
w("")
w(f"{len(ops)} operations across {len(set(o['path'] for o in ops))} paths, "
  f"{len(schemas)} schemas. **`FROZEN` means another team is already building against it: "
  "changing one needs a cohort conversation.**")
w("")

for g in ["Public institutions", "OPDS institution", "OPDS public", "App", "Admin"]:
    rows = [o for o in ops if group(o["path"]) == g]
    if not rows: continue
    w(f"## {g}")
    w("")
    w("| | Path | Stability | Called by |")
    w("|---|---|---|---|")
    for o in rows:
        cb = o["called_by"] or "wokay"
        w(f"| {o['method']} | `{o['path']}` | {o['stability'] or '-'} | {cb} |")
    w("")

w("## Enums a client switches on")
w("")
for k, v in enums.items():
    w(f"- **`{k}`**: {v}")
w("")

w("## Error codes, all of them")
w("")
w(", ".join(f"`{c}`" for c in codes) if codes else "none found")
w("")
w("Every one is reachable. There are no spare codes, so do not write a handler for a code that is "
  "not in this list.")
w("")

# --- auth mechanics: the part a console screen cannot guess ------------------
auth = []
if "adminRefresh" in src:
    m = re.search(r"example: >-\s*\n\s*(adminRefresh=[^\n]*(?:\n\s{16,}[^\n]*)?)", src)
    cookie = " ".join(m.group(1).split()) if m else "adminRefresh=...; HttpOnly; Secure; SameSite=Strict"
    auth.append(("Refresh token delivery", "an **`HttpOnly` cookie** named `adminRefresh`, set by login and rotated by refresh"))
    auth.append(("Why a cookie", "a store JavaScript can read is a store an XSS can read. `HttpOnly` is the only one it cannot, so **a console reload survives** without the token ever entering JavaScript"))
    auth.append(("Cookie attributes", "`" + cookie + "`"))
    auth.append(("Reading it from JS", "**you cannot, and must not try.** The browser sends it on its own. Call refresh with no body and let the cookie do the work"))
    auth.append(("Body fallback", "`refreshToken` in the body still works, for a non-browser caller. Neither is required; presenting neither is a `401`, not a `400`"))
if auth:
    w("## Auth mechanics")
    w("")
    w("| | |")
    w("|---|---|")
    for k, v in auth:
        w(f"| **{k}** | {v} |")
    w("")

w("## Schemas")
w("")
w(", ".join(f"`{s}`" for s in schemas))
w("")
w("## When this is not enough")
w("")
w(f"Read the relevant part of `{src_path}` for exact field names, required flags and examples. "
  "**Do not read the whole file**: it is about 147 KB, and grep or a targeted read is always cheaper.")

open(out_path, "w", encoding="utf-8").write("\n".join(L) + "\n")

lines = len(L) + 1
print(f"wrote {out_path}: {lines} lines, {len(ops)} operations, {len(schemas)} schemas, {len(codes)} error codes")
if lines > 140:
    print(f"WARNING: {lines} lines is larger than intended. Trim the schema list if it keeps growing.")
PY
