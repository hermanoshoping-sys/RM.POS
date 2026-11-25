#!/usr/bin/env bash
# tools/purge_git_history.sh
#
# ينظّف تاريخ Git من أي ملفات أو نصوص مفاتيح خاصة سابقة باستخدام git-filter-repo.
# شغّل هذا السكربت على نسخة مستودع Git المنشور أو المشترك، ثم ارفع التاريخ المنظّف بـ --force.

set -euo pipefail

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "[!] هذا المجلد ليس مستودع Git (.git غير موجود هنا)."
  echo "    شغّل السكربت داخل نسخة المستودع المنشور لديك إن وُجد."
  exit 1
fi

if ! command -v git-filter-repo >/dev/null 2>&1; then
  echo "[!] الأداة git-filter-repo غير مثبّتة. ثبّتها أولاً عبر: pip install git-filter-repo"
  exit 1
fi

REPL_FILE="$(mktemp)"
trap 'rm -f "$REPL_FILE"' EXIT

cat > "$REPL_FILE" <<'EOF'
regex:MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEH[A-Za-z0-9+/=]{60,}==>[REDACTED_ROTATED_PRIVATE_KEY]
regex:MHcCAQEEI[A-Za-z0-9+/=]{50,}==>[REDACTED_ROTATED_PRIVATE_KEY]
EOF

echo "[+] جاري حذف ملفات المفاتيح الخاصة وتنقيح النصوص من كامل تاريخ Git..."
git filter-repo --force \
  --invert-paths \
  --path-glob '*private*.pem' \
  --path-glob '*.jks' \
  --path-glob '*.key' \
  --replace-text "$REPL_FILE"

echo "[OK] تم تنظيف تاريخ Git بنجاح. لتحديث المستودع البعيد شغّل:"
echo "     git push origin --force --all && git push origin --force --tags"
