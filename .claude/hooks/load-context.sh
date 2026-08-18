#!/usr/bin/env bash
# SessionStart hook. Whatever this prints is added to the session once, at the start.
#
# Keep the output SHORT. Every line here is paid for by every member in every session.
# Print an index and pointers, never file contents.
set -uo pipefail

repo_root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$repo_root" || exit 0

team_file=".claude/team"

if [ ! -f "$team_file" ]; then
  cat <<'MSG'
SETUP INCOMPLETE: .claude/team does not exist.

Create it with one word, so the right team context loads:
    echo wokay    > .claude/team      # or
    echo flambeau > .claude/team

The file is gitignored, so it is per person and never conflicts.
MSG
else
  team=$(tr -d '[:space:]' < "$team_file")
  ctx=".claude/context/${team}.md"
  if [ -f "$ctx" ]; then
    echo "Team: ${team}"
    cat "$ctx"
  else
    echo "Team '${team}' is set but ${ctx} does not exist yet."
    echo "Valid values are the basenames in .claude/context/ :"
    ls .claude/context/ 2>/dev/null | sed 's/\.md$//' | sed 's/^/    /'
  fi
fi

# --- fail fast on the attribution rule, before any code is written -------------
name=$(git config user.name  2>/dev/null || true)
email=$(git config user.email 2>/dev/null || true)
if [ -z "$name" ] || [ -z "$email" ]; then
  cat <<'MSG'

BLOCKER: this clone has no git identity, so no commit can be made.
Set it before writing code:
    git config user.name  "Your Name"
    git config user.email "your@email"
MSG
fi

# --- warn if the git-side hooks are not wired up -------------------------------
hookspath=$(git config core.hooksPath 2>/dev/null || true)
if [ "$hookspath" != ".githooks" ] && [ -d .githooks ]; then
  echo
  echo "WARNING: .githooks/ exists but is not active. Enable it once:"
  echo "    git config core.hooksPath .githooks"
fi

# --- warn if the generated contract digest is missing or stale -----------------
digest=".claude/context/api-contract-digest.md"
yaml="api-docs/wokay-api.yaml"
if [ -f "$yaml" ] && [ -f "$digest" ] && [ "$yaml" -nt "$digest" ]; then
  echo
  echo "NOTE: $yaml is newer than $digest. Run ./scripts/gen-api-digest.sh"
fi

exit 0
