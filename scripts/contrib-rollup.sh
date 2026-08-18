#!/usr/bin/env bash
# Regenerates CONTRIBUTIONS.md from .contrib/*.log plus git history.
#
# Why per-person files and not one shared table: five people appending rows to one table
# conflicts on nearly every PR, and that conflict is the one a fresher team resolves badly.
# Two people never touch the same file here, so the conflict cannot happen.
#
# Run weekly, or in CI on push to main. Never edit CONTRIBUTIONS.md by hand.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
OUT="CONTRIBUTIONS.md"
mkdir -p .contrib

# Reverse lines, portably. `tac` is GNU and absent on macOS, where everyone on this team
# works; `tail -r` is BSD and absent on Linux CI. The sed form works on both.
rev_lines() { sed '1!G;h;$!d'; }

{
  echo "# Contributions"
  echo
  echo "**GENERATED FILE. Do not edit.** Run \`./scripts/contrib-rollup.sh\`."
  echo "Source: \`.contrib/*.log\`, one append-only file per person, written by \`/ship\`."
  echo

  # ---- per person, most recent first ----------------------------------------
  shopt -s nullglob
  logs=(.contrib/*.log)
  if [ ${#logs[@]} -eq 0 ]; then
    echo "No entries yet. \`/ship\` writes one line per commit."
    echo
  else
    echo "## By person"
    echo
    for f in "${logs[@]}"; do
      person=$(basename "$f" .log)
      count=$(grep -cve '^[[:space:]]*$' "$f" || true)
      echo "### ${person}  (${count} entries)"
      echo
      echo "| Date | Team | Branch | Kind | What | PR |"
      echo "|---|---|---|---|---|---|"
      # newest first, skip blanks, guard against a malformed line
      grep -ve '^[[:space:]]*$' "$f" | rev_lines | while IFS='|' read -r d t b k s p; do
        [ -z "${p:-}" ] && p="-"
        printf '| %s | %s | `%s` | %s | %s | %s |\n' \
          "${d:-?}" "${t:-?}" "${b:-?}" "${k:-?}" "${s:-?}" "$p"
      done
      echo
    done
  fi

  # ---- what git itself says, as a cross-check --------------------------------
  echo "## Commits per author, from git"
  echo
  echo "A cross-check on the table above. If a name appears here but not there, \`/ship\` was"
  echo "bypassed. If a name looks wrong, that person's \`git config user.name\` needs fixing."
  echo
  echo '```'
  # HEAD and </dev/null are both required. git shortlog reads commit entries from STDIN
  # when stdin is not a terminal, so in CI or any non-interactive run it hangs forever
  # waiting for input. Passing a revision and closing stdin makes it deterministic.
  git --no-pager shortlog -sne --no-merges HEAD < /dev/null | sed 's/^ *//'
  echo '```'
  echo

  # ---- anything attributed to a bot is a rule violation ----------------------
  if git --no-pager log --format='%ae %an' < /dev/null | grep -qiE 'anthropic|claude|noreply'; then
    echo "## RULE VIOLATION"
    echo
    echo "Commits below are attributed to Claude rather than a person. Every commit on this"
    echo "project must belong to a human. Check that \`.githooks/commit-msg\` is active:"
    echo "\`git config core.hooksPath .githooks\`"
    echo
    echo '```'
    git --no-pager log --format='%h %an <%ae> %s' < /dev/null | grep -iE 'anthropic\.com|claude' | head -20
    echo '```'
    echo
  fi

  echo "## Format"
  echo
  echo "One line per commit, pipe delimited, appended by \`/ship\`:"
  echo
  echo '```'
  echo 'YYYY-MM-DD|team|branch|kind|summary, 60 chars max|#PR or -'
  echo '```'
  echo
  echo "\`kind\` is one of: \`feat\` \`fix\` \`test\` \`docs\` \`chore\` \`refactor\`. No others."
} > "$OUT"

echo "wrote $OUT"
lines=$(wc -l < "$OUT" | tr -d ' ')
entries=$(cat .contrib/*.log 2>/dev/null | grep -cve '^[[:space:]]*$' || echo 0)
echo "  $lines lines, $entries entries across $(ls .contrib/*.log 2>/dev/null | wc -l | tr -d ' ') people"
