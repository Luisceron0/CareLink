#!/usr/bin/env bash
set -euo pipefail

FILES=(
  ".env.example"
  "tasks/PROJECT_PLAN.md"
  ".github/copilot-instructions.md"
  ".github/workflows/ci.yml"
  "carelink-srs.md"
  "SECURITY.md"
  "docs/adr/ADR-008.md"
  "scripts/prepare_commit.sh"
  "tasks/todo.md"
)

echo "Adding files to git..."
git add "${FILES[@]}"

git commit -m "chore(infra): Adopt Railway/Supabase/Upstash/Confluent; add ADR-008 and CI updates"

echo "Commit created. To push: git push origin main"

exit 0
