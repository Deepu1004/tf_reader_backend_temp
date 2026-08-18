#!/usr/bin/env bash
# PreToolUse hook on Bash. Denies commands that would break a non-negotiable rule.
#
# Reads the tool call as JSON on stdin. Emits a deny decision as JSON on stdout.
# Deny, not "ask", on purpose: a fresher faced with a permission prompt they do not
# understand will approve it.
#
# VERIFY the field names against your Claude Code version with /hooks before trusting this.
# If the schema differs, the hook silently does nothing, which is worse than an error.
set -uo pipefail

payload=$(cat)

# Pull the command out. jq if available, a crude fallback if not.
if command -v jq >/dev/null 2>&1; then
  cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // ""')
else
  cmd=$(printf '%s' "$payload" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/p')
fi
[ -z "$cmd" ] && exit 0

deny() {
  # Escape for JSON: backslashes first, then quotes, then newlines.
  reason=$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{printf "%s\\n", $0}')
  cat <<JSON
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"$reason"}}
JSON
  exit 0
}

# --- rule 2: never merge a pull request ---------------------------------------
if printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])gh[[:space:]]+pr[[:space:]]+merge'; then
  deny "BLOCKED. Claude must never merge a pull request on this project. Open the PR and stop; a human merges it on GitHub after review."
fi
if printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+merge'; then
  deny "BLOCKED. git merge is not allowed from a session. If you need upstream changes, use the documented sync steps in docs/FORK-SYNC.md and let a human run them."
fi
if printf '%s' "$cmd" | grep -qE 'gh[[:space:]]+api.*/(merges|merge)([[:space:]]|$|")'; then
  deny "BLOCKED. That is a merge API call. Claude must never merge a pull request on this project."
fi

# --- rule 1: the commit is authored by ONE team member, alone ------------------
# Every route that could reattribute a commit or add a second author.

if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+commit.*--author'; then
  deny "BLOCKED. Never pass --author. The commit must carry this clone's own git identity, so it is attributed to the team member who ran it."
fi

# `git -c user.email=... commit` overrides the config for one command, which would slip past
# a config-only check entirely.
if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+(-c|--config-env)[[:space:]]*[= ]*user\.(name|email)'; then
  deny "BLOCKED. Do not override the git identity for a single command. Commit as yourself, using the identity already configured in this clone."
fi

# The env-var route to the same thing.
if printf '%s' "$cmd" | grep -qE '(GIT_AUTHOR_(NAME|EMAIL)|GIT_COMMITTER_(NAME|EMAIL))[[:space:]]*='; then
  deny "BLOCKED. Do not set GIT_AUTHOR_* or GIT_COMMITTER_*. Those reattribute the commit to somebody who did not write it."
fi

# ANY co-author trailer, whoever it names. A second author means the commit is not authored by
# one team member alone, which is the rule on this project.
if printf '%s' "$cmd" | grep -qiE 'co-?authored-?by[[:space:]]*:'; then
  deny "BLOCKED. No co-author trailer, for anybody. Every commit here is authored by ONE team member, alone, with no trailers at all. If two people worked on it, say so in the message body in your own words."
fi

# Every other attribution trailer, and the free-text tool credit.
if printf '%s' "$cmd" | grep -qiE '(generated-by|assisted-by|authored-by|helped-by|signed-off-by|reviewed-by|on-behalf-of)[[:space:]]*:'; then
  deny "BLOCKED. This project uses no commit trailers at all, so the author is unambiguous and every message reads the same way."
fi
if printf '%s' "$cmd" | grep -qiE '(generated|created|written|made|built)[[:space:]]+with[[:space:]]+.*(claude|anthropic|copilot|cursor|chatgpt)'; then
  deny "BLOCKED. No tool credit in a commit message. The commit is the team member's own work as far as the repository is concerned."
fi
if printf '%s' "$cmd" | grep -qiE 'noreply@anthropic\.com|@anthropic\.com'; then
  deny "BLOCKED. No anthropic.com address in a commit. Commits are attributed to a person on this team."
fi

# Setting somebody's identity FOR them. Tell them the command instead, so the identity is
# genuinely theirs and they know it was set.
if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+config[[:space:]]+(--global[[:space:]]+|--local[[:space:]]+|--system[[:space:]]+)?user\.(name|email)[[:space:]]+'; then
  deny "BLOCKED. Do not set the git identity for the user. Print the command and let them run it, so the identity is genuinely theirs."
fi

# Rewriting history reattributes past commits wholesale.
if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+(filter-branch|filter-repo)|git[[:space:]]+rebase.*--exec'; then
  deny "BLOCKED. That rewrites history and would reattribute commits that already belong to somebody. A human does this deliberately, never a session."
fi
# Note the `--` before the pattern. Without it grep reads "--author" as one of its own
# options and errors out, which silently disables the check.
if printf '%s' "$cmd" | grep -qE -- 'git[[:space:]]+commit.*--amend' \
   && printf '%s' "$cmd" | grep -qE -- '--author|--reset-author'; then
  deny "BLOCKED. Amending the author of an existing commit reattributes somebody else's work."
fi

# --- history safety -----------------------------------------------------------
if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+push.*(--force|-f([[:space:]]|$))'; then
  deny "BLOCKED. No force push. If history needs rewriting, a human does it deliberately."
fi
if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+-[a-z]*f)'; then
  deny "BLOCKED. That discards uncommitted work irreversibly. Show the user what would be lost and let them decide."
fi
if printf '%s' "$cmd" | grep -qE 'git[[:space:]]+push.*(:main|[[:space:]]main([[:space:]]|$))' \
   && ! printf '%s' "$cmd" | grep -qE '\-u[[:space:]]+origin[[:space:]]+[a-z]+/'; then
  deny "BLOCKED. Never push directly to main. Push your feature branch and open a PR."
fi

exit 0
