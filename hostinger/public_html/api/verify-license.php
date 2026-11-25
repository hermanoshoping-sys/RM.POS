<?php
declare(strict_types=1);

/**
 * RM.POS Online License Verification & Renewal Endpoint for Hostinger.
 *
 * Configure Android release build with:
 *   export LICENSE_SERVER_URL="https://your-domain.com/api/verify-license.php"
 *
 * Request (POST application/json):
 *   { "device_id": "16_HEX", "license_key": "<b64>.<sig>", "timestamp": <epoch_ms> }
 *
 * Responses match LicenseOnlineClient.kt:
 *   Active:  { "valid": true,  "server_time": <ms>, "signed_token": "<b64>.<sig>", "edition": "PRO", "expires": <ms> }
 *   Revoked: { "valid": false, "server_time": <ms>, "revocation_token": "<b64>.<sig>", "message": "..." }
 */

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/crypto.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    rmpos_json_response(405, ['error' => 'Method Not Allowed']);
}

$nowMs = rmpos_now_ms();
$clientIp = rmpos_client_ip();

try {
    $config = rmpos_load_config();
    $pdo = rmpos_db($config);
} catch (Throwable $e) {
    error_log('[RM.POS verify-license] Bootstrap error: ' . $e->getMessage());
    rmpos_json_response(503, [
        'valid' => false,
        'server_time' => $nowMs,
        'message' => 'Service temporarily unavailable',
    ]);
}

// Per-IP sliding window rate limit (60s)
$rateLimit = max(5, (int) ($config['rate_limit_per_minute'] ?? 60));
try {
    $rateStmt = $pdo->prepare(
        'SELECT COUNT(*) FROM verification_logs WHERE client_ip = :ip AND created_at >= (NOW() - INTERVAL 1 MINUTE)'
    );
    $rateStmt->execute([':ip' => $clientIp]);
    if ((int) $rateStmt->fetchColumn() >= $rateLimit) {
        rmpos_json_response(429, [
            'valid' => false,
            'server_time' => $nowMs,
            'message' => 'Too many requests',
        ]);
    }
} catch (Throwable $e) {
    error_log('[RM.POS verify-license] Rate limit query error: ' . $e->getMessage());
}

$rawBody = file_get_contents('php://input');
if ($rawBody === false || strlen($rawBody) > 16384) {
    rmpos_json_response(400, ['valid' => false, 'server_time' => $nowMs, 'message' => 'Invalid payload size']);
}

$input = json_decode($rawBody, true);
if (!is_array($input)) {
    rmpos_json_response(400, ['valid' => false, 'server_time' => $nowMs, 'message' => 'Invalid JSON']);
}

$deviceId = rmpos_normalize_device_id((string) ($input['device_id'] ?? ''));
$licenseKey = trim((string) ($input['license_key'] ?? ''));
$clientTs = isset($input['timestamp']) && is_numeric($input['timestamp']) ? (int) $input['timestamp'] : null;

if (!rmpos_is_valid_device_id($deviceId) || $licenseKey === '') {
    rmpos_json_response(400, [
        'valid' => false,
        'server_time' => $nowMs,
        'message' => 'Invalid device_id or license_key',
    ]);
}

$logOutcome = static function (string $outcome) use ($pdo, $deviceId, $clientIp, $clientTs, $nowMs): void {
    try {
        $stmt = $pdo->prepare(
            'INSERT INTO verification_logs (device_id, outcome, client_ip, client_timestamp_ms, server_time_ms)
             VALUES (:device_id, :outcome, :client_ip, :client_ts, :server_time)'
        );
        $stmt->execute([
            ':device_id' => $deviceId,
            ':outcome' => $outcome,
            ':client_ip' => $clientIp,
            ':client_ts' => $clientTs,
            ':server_time' => $nowMs,
        ]);
    } catch (Throwable $e) {
        error_log('[RM.POS verify-license] Audit log error: ' . $e->getMessage());
    }
};

try {
    // 1. Check authoritative database record for this device
    $stmt = $pdo->prepare('SELECT * FROM licenses WHERE device_id = :device_id LIMIT 1');
    $stmt->execute([':device_id' => $deviceId]);
    $row = $stmt->fetch();

    if ($row !== false) {
        $upd = $pdo->prepare('UPDATE licenses SET last_seen_at = NOW(), last_ip = :ip WHERE id = :id');
        $upd->execute([':ip' => $clientIp, ':id' => $row['id']]);

        // If explicitly revoked by administrator -> return cryptographically signed REVOKE token
        if (($row['status'] ?? '') === 'REVOKED') {
            $reason = (string) ($row['revocation_reason'] ?? 'تم إلغاء الاشتراك من الإدارة');
            $revToken = rmpos_sign_revocation_token($deviceId, $reason, $config, $nowMs);
            $logOutcome('REVOKED');
            rmpos_json_response(200, [
                'valid' => false,
                'server_time' => $nowMs,
                'revocation_token' => $revToken,
                'message' => $reason,
            ]);
        }

        $expiresAtMs = (int) $row['expires_at_ms'];
        if ($nowMs > $expiresAtMs) {
            $logOutcome('EXPIRED');
            rmpos_json_response(200, [
                'valid' => false,
                'server_time' => $nowMs,
                'message' => 'انتهت صلاحية الاشتراك، يرجى التجديد.',
            ]);
        }

        // Verify caller actually holds a valid token signed by our key for this device
        $incomingPayload = rmpos_verify_incoming_token($licenseKey, $config);
        $incomingDevice = rmpos_normalize_device_id((string) ($incomingPayload['device_id'] ?? ''));
        if ($incomingPayload === null || $incomingDevice !== $deviceId) {
            $logOutcome('INVALID_CLIENT_TOKEN');
            rmpos_json_response(403, [
                'valid' => false,
                'server_time' => $nowMs,
                'message' => 'رمز الترخيص المرسل غير صالح لهذا الجهاز',
            ]);
        }

        $edition = (string) ($row['edition'] ?? 'PRO');
        $graceDays = (int) ($row['grace_days'] ?? ($config['default_grace_days'] ?? 30));
        $renewedToken = rmpos_sign_license_token($deviceId, $edition, $expiresAtMs, $graceDays, $config, $nowMs);

        $logOutcome('RENEWED');
        rmpos_json_response(200, [
            'valid' => true,
            'server_time' => $nowMs,
            'signed_token' => $renewedToken,
            'edition' => $edition,
            'expires' => $expiresAtMs,
        ]);
    }

    // 2. Device not yet in DB: accept valid offline-signed tokens (issued via CLI/panel) without revoking
    $incomingPayload = rmpos_verify_incoming_token($licenseKey, $config);
    if ($incomingPayload === null) {
        $logOutcome('UNKNOWN_INVALID');
        rmpos_json_response(403, [
            'valid' => false,
            'server_time' => $nowMs,
            'message' => 'ترخيص غير معروف أو توقيع غير معتمد',
        ]);
    }

    $incomingDevice = rmpos_normalize_device_id((string) ($incomingPayload['device_id'] ?? ''));
    $incomingExpires = (int) ($incomingPayload['expires'] ?? 0);
    $incomingEdition = strtoupper((string) ($incomingPayload['type'] ?? 'PRO'));
    if (!in_array($incomingEdition, ['STANDARD', 'PRO', 'ENTERPRISE'], true)) {
        $incomingEdition = 'PRO';
    }

    if ($incomingDevice !== $deviceId || $incomingExpires <= $nowMs) {
        $logOutcome('UNKNOWN_EXPIRED_OR_MISMATCH');
        rmpos_json_response(200, [
            'valid' => false,
            'server_time' => $nowMs,
            'message' => 'انتهت صلاحية الترخيص أو لا يطابق هذا الجهاز',
        ]);
    }

    $graceDays = (int) ($config['default_grace_days'] ?? 30);
    $ins = $pdo->prepare(
        'INSERT INTO licenses (device_id, customer_name, edition, status, issued_at_ms, expires_at_ms, grace_days, last_seen_at, last_ip)
         VALUES (:device_id, :customer_name, :edition, "ACTIVE", :issued_at, :expires_at, :grace_days, NOW(), :ip)'
    );
    $ins->execute([
        ':device_id' => $deviceId,
        ':customer_name' => 'Auto-Registered Device',
        ':edition' => $incomingEdition,
        ':issued_at' => (int) ($incomingPayload['issued_at'] ?? $nowMs),
        ':expires_at' => $incomingExpires,
        ':grace_days' => $graceDays,
        ':ip' => $clientIp,
    ]);

    $renewedToken = rmpos_sign_license_token($deviceId, $incomingEdition, $incomingExpires, $graceDays, $config, $nowMs);
    $logOutcome('AUTO_REGISTERED_RENEWED');
    rmpos_json_response(200, [
        'valid' => true,
        'server_time' => $nowMs,
        'signed_token' => $renewedToken,
        'edition' => $incomingEdition,
        'expires' => $incomingExpires,
    ]);
} catch (Throwable $e) {
    error_log('[RM.POS verify-license] Execution error: ' . $e->getMessage());
    rmpos_json_response(500, [
        'valid' => false,
        'server_time' => $nowMs,
        'message' => 'Internal server error',
    ]);
}
