#!/usr/bin/env bash
# tools/install_git_hooks.sh
# Installs a pre-commit hook enforcing gitleaks + tools/check_secrets_precommit.py
# so no private key or release keystore can ever be committed.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -d "$REPO_ROOT/.git" ]; then
  echo "[!] No .git directory found at $REPO_ROOT. Initialize git first (git init), then re-run this script."
  exit 1
fi

HOOK_PATH="$REPO_ROOT/.git/hooks/pre-commit"
mkdir -p "$(dirname "$HOOK_PATH")"

cat > "$HOOK_PATH" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel)"
python3 "$REPO_ROOT/tools/check_secrets_precommit.py"
EOF

chmod +x "$HOOK_PATH"
chmod +x "$REPO_ROOT/tools/check_secrets_precommit.py"
echo "[OK] Installed Git pre-commit secret guard at $HOOK_PATH"
