#!/usr/bin/env python3
"""
tools/license_generator.py

Cryptographic ECDSA (P-256 / SHA256withECDSA) License & Recovery Generator for RM.POS.
Unsigned or human-formatted keys (e.g., RM.POS@...) are strictly prohibited.
Every license token is cryptographically signed and includes:
  - kid: Key ID for safe public-key rotation (default: k2026-v2)
  - device_id: 16-hex hardware-bound Device ID
  - type: Edition (STANDARD, PRO, ENTERPRISE)
  - issued_at: Issuance epoch ms
  - expires: Mandatory positive expiration epoch ms
  - grace_until: Signed offline grace deadline epoch ms (renewed periodically online)

Usage (run from or output private key to a secure directory OUTSIDE the project):
  1. Generate a new rotated P-256 keypair outside the project archive:
     mkdir -p ~/rmpos-key-vault && chmod 700 ~/rmpos-key-vault
     python3 tools/license_generator.py generate-keys --kid k2026-v2 --priv ~/rmpos-key-vault/k2026-v2.pem --pub license_public_key.pem

  2. Verify that license_public_key.pem matches LicenseSecurityManager.kt:
     python3 tools/license_generator.py verify-keypair --priv ~/rmpos-key-vault/k2026-v2.pem

  3. Sign a device-bound license token:
     python3 tools/license_generator.py sign-license --key ~/rmpos-key-vault/k2026-v2.pem --device-id 4F3A8B1C9D2E0F5A --type PRO --days 365 --grace-days 30 --kid k2026-v2

  4. Sign a one-time emergency recovery token:
     python3 tools/license_generator.py sign-recovery --key ~/rmpos-key-vault/k2026-v2.pem --device-id 4F3A8B1C9D2E0F5A --user admin --hours 24 --kid k2026-v2
"""

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import time

DEFAULT_KEY_ID = "k2026-v2"
PROJECT_ROOT = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
KOTLIN_MANAGER_PATH = os.path.join(PROJECT_ROOT, "app/src/main/java/com/example/security/LicenseSecurityManager.kt")


def run_cmd(cmd, input_data=None):
    res = subprocess.run(cmd, input=input_data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if res.returncode != 0:
        raise RuntimeError(f"Command {' '.join(cmd)} failed: {res.stderr.decode('utf-8', errors='ignore')}")
    return res.stdout


def read_pem_base64(pem_path):
    with open(pem_path, "r", encoding="utf-8") as f:
        lines = f.readlines()
    return "".join(line.strip() for line in lines if not line.startswith("-----"))


def assert_outside_project(private_path):
    abs_priv = os.path.realpath(os.path.abspath(private_path))
    if abs_priv == PROJECT_ROOT or abs_priv.startswith(PROJECT_ROOT + os.sep):
        print(f"[SECURITY ERROR] Refusing to store or read private key inside project directory: {abs_priv}")
        print("Archive exports ignore .gitignore. Store private keys in a secure folder OUTSIDE the project (e.g. ~/rmpos-key-vault/).")
        sys.exit(1)


def generate_keys(private_path, public_path=os.path.join(PROJECT_ROOT, "license_public_key.pem"), kid=DEFAULT_KEY_ID):
    assert_outside_project(private_path)
    priv_dir = os.path.dirname(os.path.abspath(private_path))
    if priv_dir and not os.path.exists(priv_dir):
        os.makedirs(priv_dir, mode=0o700, exist_ok=True)
    print(f"[+] Generating ECDSA P-256 (prime256v1) keypair for kid='{kid}'...")
    run_cmd(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", private_path])
    os.chmod(private_path, 0o600)
    run_cmd(["openssl", "ec", "-in", private_path, "-pubout", "-out", public_path])

    clean_b64 = read_pem_base64(public_path)
    print(f"[OK] Private key saved OUTSIDE project at: {private_path} (chmod 600)")
    print(f"[OK] Public key saved to: {public_path}")
    print("\n" + "=" * 68)
    print("REGISTER THIS KEY IN LicenseSecurityManager.kt TRUSTED_PUBLIC_KEYS:")
    print(f'"{kid}" to "{clean_b64}"')
    print("=" * 68 + "\n")


def verify_keypair(public_path="license_public_key.pem", private_path=None):
    if not os.path.exists(public_path):
        print(f"[!] Public key file not found: {public_path}")
        sys.exit(1)

    pem_b64 = read_pem_base64(public_path)
    if os.path.exists(KOTLIN_MANAGER_PATH):
        with open(KOTLIN_MANAGER_PATH, "r", encoding="utf-8") as f:
            kt_source = f.read()
        match = re.search(r'PUBLIC_KEY_BASE64\s*=\s*"([^"]+)"', kt_source)
        if not match:
            print(f"[!] Could not locate PUBLIC_KEY_BASE64 in {KOTLIN_MANAGER_PATH}")
            sys.exit(1)
        kt_b64 = match.group(1).strip()
        if pem_b64 != kt_b64:
            print("[!] MISMATCH DETECTED between license_public_key.pem and LicenseSecurityManager.kt!")
            print(f"    PEM:    {pem_b64}")
            print(f"    Kotlin: {kt_b64}")
            sys.exit(1)
        print("[OK] license_public_key.pem matches LicenseSecurityManager.PUBLIC_KEY_BASE64")

    if private_path and os.path.exists(private_path):
        derived_pub = run_cmd(["openssl", "ec", "-in", private_path, "-pubout"]).decode("utf-8")
        derived_b64 = "".join(
            line.strip() for line in derived_pub.splitlines() if not line.startswith("-----")
        )
        if derived_b64 != pem_b64:
            print("[!] MISMATCH DETECTED: private key does not correspond to license_public_key.pem!")
            sys.exit(1)
        print("[OK] Private key matches license_public_key.pem")


def sign_data_with_openssl(private_key_path, data_bytes):
    sig_der = run_cmd(["openssl", "dgst", "-sha256", "-sign", private_key_path], input_data=data_bytes)
    return base64.b64encode(sig_der).decode("utf-8")


def sign_license(private_key_path, device_id, license_type="PRO", days=365, grace_days=30, kid=DEFAULT_KEY_ID):
    assert_outside_project(private_key_path)
    if not os.path.exists(private_key_path):
        print(f"[!] Error: Private key file not found at {private_key_path}")
        sys.exit(1)

    clean_device = device_id.strip().upper()
    if not re.fullmatch(r"[0-9A-F]{16}", clean_device):
        print("[!] Error: --device-id must be a 16-character hexadecimal Device ID.")
        sys.exit(1)
    if days <= 0 or days > 3650:
        print("[!] Error: --days must be between 1 and 3650 (explicit expiration required).")
        sys.exit(1)
    if grace_days <= 0 or grace_days > days:
        grace_days = min(max(1, grace_days), days)

    now_ms = int(time.time() * 1000)
    expires_ts = now_ms + (days * 86400 * 1000)
    grace_until_ts = min(expires_ts, now_ms + (grace_days * 86400 * 1000))

    payload = {
        "kid": kid,
        "device_id": clean_device,
        "type": license_type,
        "issued_at": now_ms,
        "expires": expires_ts,
        "grace_until": grace_until_ts,
    }

    payload_json = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    payload_b64 = base64.b64encode(payload_json).decode("utf-8")
    sig_b64 = sign_data_with_openssl(private_key_path, payload_json)
    token = f"{payload_b64}.{sig_b64}"

    print("\n" + "=" * 68)
    print(f"SIGNED ECDSA ACTIVATION TOKEN (kid={kid})")
    print(f"Device ID:    {clean_device}")
    print(f"Edition:      {license_type}")
    print(f"Expires:      {time.ctime(expires_ts / 1000)}")
    print(f"Offline Grace:{time.ctime(grace_until_ts / 1000)} ({grace_days} days)")
    print("-" * 68)
    print(token)
    print("=" * 68 + "\n")
    return token


def sign_recovery(private_key_path, device_id, user="admin", hours=24, kid=DEFAULT_KEY_ID):
    assert_outside_project(private_key_path)
    if not os.path.exists(private_key_path):
        print(f"[!] Error: Private key file not found at {private_key_path}")
        sys.exit(1)

    clean_device = device_id.strip().upper()
    if not re.fullmatch(r"[0-9A-F]{16}", clean_device):
        print("[!] Error: --device-id must be a 16-character hexadecimal Device ID.")
        sys.exit(1)
    if hours <= 0 or hours > 168:
        print("[!] Error: --hours must be between 1 and 168.")
        sys.exit(1)

    now_ms = int(time.time() * 1000)
    expires_ts = now_ms + (hours * 3600 * 1000)

    payload = {
        "kid": kid,
        "action": "RECOVERY",
        "device_id": clean_device,
        "target_user": user,
        "issued_at": now_ms,
        "expires": expires_ts,
    }

    payload_json = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    payload_b64 = base64.b64encode(payload_json).decode("utf-8")
    sig_b64 = sign_data_with_openssl(private_key_path, payload_json)
    token = f"{payload_b64}.{sig_b64}"

    print("\n" + "=" * 68)
    print(f"SIGNED EMERGENCY RECOVERY TOKEN (kid={kid})")
    print(f"Device ID:    {clean_device}")
    print(f"Target User:  {user}")
    print(f"Expires:      {time.ctime(expires_ts / 1000)} ({hours}h)")
    print("-" * 68)
    print(token)
    print("=" * 68 + "\n")
    return token


def main():
    parser = argparse.ArgumentParser(description="RM.POS ECDSA Cryptographic License & Recovery Generator")
    subparsers = parser.add_subparsers(dest="command")

    default_vault_priv = os.path.expanduser("~/rmpos-key-vault/license_private_key_k2026_v2.pem")
    default_pub = os.path.join(PROJECT_ROOT, "license_public_key.pem")

    gen_parser = subparsers.add_parser("generate-keys", help="Generate ECDSA P-256 keypair outside the project")
    gen_parser.add_argument("--priv", default=default_vault_priv, help="Output private key path (MUST be outside project)")
    gen_parser.add_argument("--pub", default=default_pub, help="Output public key path")
    gen_parser.add_argument("--kid", default=DEFAULT_KEY_ID, help="Key identifier (kid)")

    ver_parser = subparsers.add_parser("verify-keypair", help="Verify PEM matches embedded Kotlin public key")
    ver_parser.add_argument("--pub", default=default_pub, help="Public key PEM path")
    ver_parser.add_argument("--priv", default=None, help="Optional private key PEM path to cross-check")

    sign_parser = subparsers.add_parser("sign-license", help="Sign an ECDSA device-bound activation token")
    sign_parser.add_argument("--device-id", required=True, help="16-hex Device ID shown in app")
    sign_parser.add_argument("--type", default="PRO", help="Edition (STANDARD, PRO, ENTERPRISE)")
    sign_parser.add_argument("--days", type=int, default=365, help="Total validity in days (1..3650)")
    sign_parser.add_argument("--grace-days", type=int, default=30, help="Signed offline grace period in days")
    sign_parser.add_argument("--kid", default=DEFAULT_KEY_ID, help="Key identifier (kid)")
    sign_parser.add_argument("--key", required=True, help="External private key PEM path (outside project)")

    rec_parser = subparsers.add_parser("sign-recovery", help="Sign a one-time ECDSA emergency recovery token")
    rec_parser.add_argument("--device-id", required=True, help="16-hex Device ID shown in app")
    rec_parser.add_argument("--user", default="admin", help="Target username to reset")
    rec_parser.add_argument("--hours", type=int, default=24, help="Validity in hours (1..168)")
    rec_parser.add_argument("--kid", default=DEFAULT_KEY_ID, help="Key identifier (kid)")
    rec_parser.add_argument("--key", required=True, help="External private key PEM path (outside project)")

    args = parser.parse_args()
    if args.command == "generate-keys":
        generate_keys(args.priv, args.pub, args.kid)
    elif args.command == "verify-keypair":
        verify_keypair(args.pub, args.priv)
    elif args.command == "sign-license":
        sign_license(args.key, args.device_id, args.type, args.days, args.grace_days, args.kid)
    elif args.command == "sign-recovery":
        sign_recovery(args.key, args.device_id, args.user, args.hours, args.kid)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
