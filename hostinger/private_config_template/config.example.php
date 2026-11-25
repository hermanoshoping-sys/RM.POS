<?php
declare(strict_types=1);

/**
 * RM.POS Hostinger Private Configuration Template.
 *
 * SECURITY RULE (CRITICAL):
 * Copy this file OUTSIDE public_html to:
 *   /home/<u_account>/domains/<your-domain.com>/rmpos-private/config.php
 * NEVER place this file or your .pem private key inside public_html!
 */

return [
    // Hostinger MySQL credentials (from hPanel -> Databases -> Management)
    'db_host' => '127.0.0.1',
    'db_name' => 'u000000000_rmpos',
    'db_user' => 'u000000000_rmpos_user',
    'db_pass' => 'CHANGE_ME_STRONG_DB_PASSWORD',

    // Active ECDSA P-256 key identifier matching LicenseSecurityManager.DEFAULT_KEY_ID
    'key_id' => 'k2026-v2',

    // Absolute path to your ECDSA P-256 private key PEM (stored OUTSIDE public_html with chmod 600)
    'private_key_path' => __DIR__ . '/license_private_key_k2026_v2.pem',

    // Public key PEM path (safe to verify incoming tokens; matches license_public_key.pem)
    'public_key_path' => __DIR__ . '/license_public_key.pem',

    // Admin panel username and bcrypt/Argon2id password hash
    // Generate your hash locally with:
    //   php -r 'echo password_hash("YourStrongAdminPass#2026", PASSWORD_DEFAULT) . PHP_EOL;'
    'admin_username' => 'admin',
    'admin_password_hash' => '',

    // Maximum API requests per IP per 60 seconds for /api/verify-license.php
    'rate_limit_per_minute' => 60,

    // Default signed offline grace period in days when renewing active devices
    'default_grace_days' => 30,
];
