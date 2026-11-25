#!/usr/bin/env python3
"""
tools/check_secrets_precommit.py

Automated pre-commit & archive secret guard for RM.POS.
1. Invokes `gitleaks protect --staged --config .gitleaks.toml` when `gitleaks` is installed.
2. Runs built-in deterministic regex + file checks across staged files (or `--all` across the entire
   project tree) to reject ANY private key (PEM, PKCS#8 Base64, SEC1 Base64, hardcoded constants,
   or release `.jks` keystores) before commit or archive packaging.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys

PROJECT_ROOT = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))

ALLOWLIST_REL_PATHS = {
    "debug.keystore",
    "debug.keystore.base64",
    "license_public_key.pem",
    ".gitleaks.toml",
    "tools/check_secrets_precommit.py",
}

SKIP_DIRS = {".git", ".gradle", "build", ".kotlin", ".build-outputs", ".idea"}

SECRET_PATTERNS = [
    ("PEM Private Key Header", re.compile(r"-----BEGIN (?:EC |RSA |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")),
    ("PKCS#8 EC P-256 Base64 Key", re.compile(r"MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEH[A-Za-z0-9+/=]{60,}")),
    ("SEC1 EC P-256 Base64 Key", re.compile(r"MHcCAQEEI[A-Za-z0-9+/=]{50,}")),
    ("Hardcoded Private Key Constant", re.compile(r"(?:TEST_PRIVATE_KEY|DEFAULT_MATCHING_PRIVATE_KEY_B64)\s*=\s*[\"'][A-Za-z0-9+/=]{32,}[\"']")),
]

FORBIDDEN_FILENAMES_RE = re.compile(r"(?:private.*\.pem|.*\.jks)$", re.IGNORECASE)


def get_staged_files():
    res = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
    )
    if res.returncode != 0:
        return []
    return [line.strip() for line in res.stdout.splitlines() if line.strip()]


def get_all_project_files():
    collected = []
    for root, dirs, files in os.walk(PROJECT_ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            abs_p = os.path.join(root, fn)
            rel_p = os.path.relpath(abs_p, PROJECT_ROOT).replace("\\", "/")
            collected.append(rel_p)
    return collected


def scan_files(rel_paths):
    findings = []
    for rel_p in rel_paths:
        if rel_p in ALLOWLIST_REL_PATHS:
            continue
        if FORBIDDEN_FILENAMES_RE.search(os.path.basename(rel_p)):
            findings.append(f"{rel_p}: Forbidden private key / release keystore file in project directory")
            continue

        abs_p = os.path.join(PROJECT_ROOT, rel_p)
        if not os.path.isfile(abs_p):
            continue
        try:
            with open(abs_p, "rb") as f:
                raw = f.read(2 * 1024 * 1024)
            if b"\x00" in raw[:4096]:
                continue
            text = raw.decode("utf-8", errors="ignore")
        except OSError:
            continue

        for rule_name, pattern in SECRET_PATTERNS:
            for m in pattern.finditer(text):
                line_no = text[: m.start()].count("\n") + 1
                findings.append(f"{rel_p}:{line_no}: Blocked secret ({rule_name})")
    return findings


def run_gitleaks_if_available(scan_all=False):
    gitleaks_bin = shutil.which("gitleaks")
    if not gitleaks_bin:
        return 0
    cfg = os.path.join(PROJECT_ROOT, ".gitleaks.toml")
    if scan_all:
        cmd = [gitleaks_bin, "detect", "--no-git", "--source", PROJECT_ROOT, "--config", cfg, "--redact"]
    else:
        cmd = [gitleaks_bin, "protect", "--staged", "--config", cfg, "--redact"]
    res = subprocess.run(cmd, cwd=PROJECT_ROOT)
    return res.returncode


def main():
    parser = argparse.ArgumentParser(description="RM.POS Private Key & Secret Scanner")
    parser.add_argument("--all", action="store_true", help="Scan entire project directory instead of git staged files")
    args = parser.parse_args()

    if args.all or not os.path.isdir(os.path.join(PROJECT_ROOT, ".git")):
        target_files = get_all_project_files()
        gl_code = run_gitleaks_if_available(scan_all=True)
    else:
        target_files = get_staged_files()
        gl_code = run_gitleaks_if_available(scan_all=False)

    findings = scan_files(target_files)
    if findings or gl_code != 0:
        print("\n[SECURITY BLOCK] Private key or secret material detected inside project!", file=sys.stderr)
        for item in findings:
            print(f"  - {item}", file=sys.stderr)
        print("Never store signing/licensing private keys inside the project directory.", file=sys.stderr)
        sys.exit(1)

    print(f"[OK] Secret scan passed ({len(target_files)} files checked, 0 private keys found).")


if __name__ == "__main__":
    main()
