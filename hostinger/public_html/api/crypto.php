<?php
declare(strict_types=1);

/**
 * Cryptographic ECDSA P-256 (SHA256withECDSA) signing & verification helpers
 * matching Android LicenseSecurityManager.kt and tools/license_generator.py.
 */

function rmpos_normalize_device_id(string $raw): string
{
    return strtoupper(trim($raw));
}

function rmpos_is_valid_device_id(string $deviceId): bool
{
    return (bool) preg_match('/^[0-9A-F]{16}$/', $deviceId);
}

function rmpos_load_private_key(array $config): OpenSSLAsymmetricKey
{
    $path = (string) ($config['private_key_path'] ?? '');
    if ($path === '' || !is_file($path) || !is_readable($path)) {
        throw new RuntimeException('ECDSA private key is unavailable.');
    }
    $pem = file_get_contents($path);
    if ($pem === false) {
        throw new RuntimeException('Unable to read ECDSA private key.');
    }
    $key = openssl_pkey_get_private($pem);
    if ($key === false) {
        throw new RuntimeException('Invalid ECDSA private key format.');
    }
    return $key;
}

function rmpos_load_public_key(array $config): OpenSSLAsymmetricKey
{
    $pubPath = (string) ($config['public_key_path'] ?? '');
    if ($pubPath !== '' && is_file($pubPath) && is_readable($pubPath)) {
        $pem = file_get_contents($pubPath);
        if ($pem !== false) {
            $pub = openssl_pkey_get_public($pem);
            if ($pub !== false) {
                return $pub;
            }
        }
    }

    // Derive public key from private key if public PEM path is omitted
    $priv = rmpos_load_private_key($config);
    $details = openssl_pkey_get_details($priv);
    if (!is_array($details) || empty($details['key'])) {
        throw new RuntimeException('Unable to derive ECDSA public key.');
    }
    $pub = openssl_pkey_get_public((string) $details['key']);
    if ($pub === false) {
        throw new RuntimeException('Invalid derived ECDSA public key.');
    }
    return $pub;
}

/**
 * Signs an associative payload into "<base64(json)>.<base64(der_ecdsa_sha256_sig)>".
 */
function rmpos_sign_payload(array $payload, array $config): string
{
    $json = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    if ($json === false) {
        throw new RuntimeException('Failed to encode token payload.');
    }

    $privKey = rmpos_load_private_key($config);
    $signatureDer = '';
    if (!openssl_sign($json, $signatureDer, $privKey, OPENSSL_ALGO_SHA256)) {
        throw new RuntimeException('ECDSA signing failed.');
    }

    return base64_encode($json) . '.' . base64_encode($signatureDer);
}

/**
 * Verifies an incoming license token's ECDSA signature and extracts its payload.
 * Returns null if malformed or signature verification fails.
 */
function rmpos_verify_incoming_token(string $token, array $config): ?array
{
    $clean = preg_replace('/\s+/', '', $token) ?? '';
    $parts = explode('.', $clean);
    if (count($parts) !== 2 || $parts[0] === '' || $parts[1] === '') {
        return null;
    }

    $payloadBytes = base64_decode($parts[0], true);
    $sigBytes = base64_decode($parts[1], true);
    if ($payloadBytes === false || $sigBytes === false) {
        return null;
    }

    $pubKey = rmpos_load_public_key($config);
    $ok = openssl_verify($payloadBytes, $sigBytes, $pubKey, OPENSSL_ALGO_SHA256);
    if ($ok !== 1) {
        return null;
    }

    $decoded = json_decode($payloadBytes, true);
    return is_array($decoded) ? $decoded : null;
}

function rmpos_sign_license_token(
    string $deviceId,
    string $edition,
    int $expiresAtMs,
    int $graceDays,
    array $config,
    ?int $nowMs = null
): string {
    $now = $nowMs ?? rmpos_now_ms();
    $graceDays = max(1, min(365, $graceDays));
    $graceUntil = min($expiresAtMs, $now + ($graceDays * 86400 * 1000));
    $kid = (string) ($config['key_id'] ?? 'k2026-v2');

    return rmpos_sign_payload([
        'kid' => $kid,
        'device_id' => rmpos_normalize_device_id($deviceId),
        'type' => strtoupper($edition),
        'issued_at' => $now,
        'expires' => $expiresAtMs,
        'grace_until' => $graceUntil,
    ], $config);
}

function rmpos_sign_revocation_token(
    string $deviceId,
    string $reason,
    array $config,
    ?int $nowMs = null
): string {
    $now = $nowMs ?? rmpos_now_ms();
    $kid = (string) ($config['key_id'] ?? 'k2026-v2');

    return rmpos_sign_payload([
        'kid' => $kid,
        'action' => 'REVOKE',
        'device_id' => rmpos_normalize_device_id($deviceId),
        'revoked_at' => $now,
        'issued_at' => $now,
        'expires' => $now + (7 * 86400 * 1000),
        'reason' => trim($reason) !== '' ? trim($reason) : 'تم إلغاء الاشتراك من لوحة الإدارة',
    ], $config);
}

function rmpos_sign_clock_reset_token(
    string $deviceId,
    int $validHours,
    array $config,
    ?int $nowMs = null
): string {
    $now = $nowMs ?? rmpos_now_ms();
    $hours = max(1, min(168, $validHours));
    $kid = (string) ($config['key_id'] ?? 'k2026-v2');

    return rmpos_sign_payload([
        'kid' => $kid,
        'action' => 'CLOCK_RESET',
        'device_id' => rmpos_normalize_device_id($deviceId),
        'reset_time' => $now,
        'issued_at' => $now,
        'expires' => $now + ($hours * 3600 * 1000),
    ], $config);
}

function rmpos_sign_recovery_token(
    string $deviceId,
    string $targetUser,
    int $validHours,
    array $config,
    ?int $nowMs = null
): string {
    $now = $nowMs ?? rmpos_now_ms();
    $hours = max(1, min(168, $validHours));
    $kid = (string) ($config['key_id'] ?? 'k2026-v2');
    $user = trim($targetUser) !== '' ? trim($targetUser) : 'admin';

    return rmpos_sign_payload([
        'kid' => $kid,
        'action' => 'RECOVERY',
        'device_id' => rmpos_normalize_device_id($deviceId),
        'target_user' => $user,
        'issued_at' => $now,
        'expires' => $now + ($hours * 3600 * 1000),
    ], $config);
}
