#!/bin/sh
# Install ELVO git hooks from infrastructure/scripts/git-hooks/

set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
SOURCE_DIR="$REPO_ROOT/infrastructure/scripts/git-hooks"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

for hook in commit-msg prepare-commit-msg pre-push; do
  cp "$SOURCE_DIR/$hook" "$HOOKS_DIR/$hook"
  chmod +x "$HOOKS_DIR/$hook"
  echo "Installed $hook"
done

echo "Git hooks installed successfully."
