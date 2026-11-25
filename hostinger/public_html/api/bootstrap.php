<?php
declare(strict_types=1);

/**
 * Shared bootstrap for RM.POS Hostinger API & Admin endpoints.
 * Loads configuration strictly from OUTSIDE public_html.
 */

ini_set('display_errors', '0');
error_reporting(E_ALL);

function rmpos_load_config(): array
{
    static $config = null;
    if ($config !== null) {
        return $config;
    }

    $candidates = [];
    $envPath = getenv('RMPOS_CONFIG_PATH');
    if (is_string($envPath) && trim($envPath) !== '') {
        $candidates[] = trim($envPath);
    }

    // Standard Hostinger layout:
    // /home/uXXXX/domains/your-domain.com/public_html/api/bootstrap.php
    // -> /home/uXXXX/domains/your-domain.com/rmpos-private/config.php
    $publicHtmlDir = dirname(__DIR__);
    $domainRoot = dirname($publicHtmlDir);
    $candidates[] = $domainRoot . '/rmpos-private/config.php';
    $candidates[] = dirname($domainRoot) . '/rmpos-private/config.php';

    foreach ($candidates as $path) {
        if (is_file($path) && is_readable($path)) {
            $loaded = require $path;
            if (is_array($loaded)) {
                $config = $loaded;
                return $config;
            }
        }
    }

    throw new RuntimeException('Server configuration not found outside public_html.');
}

function rmpos_db(array $config): PDO
{
    static $pdo = null;
    if ($pdo !== null) {
        return $pdo;
    }

    $host = (string) ($config['db_host'] ?? '127.0.0.1');
    $name = (string) ($config['db_name'] ?? '');
    $user = (string) ($config['db_user'] ?? '');
    $pass = (string) ($config['db_pass'] ?? '');

    $dsn = sprintf('mysql:host=%s;dbname=%s;charset=utf8mb4', $host, $name);
    $pdo = new PDO($dsn, $user, $pass, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    return $pdo;
}

function rmpos_now_ms(): int
{
    return (int) floor(microtime(true) * 1000.0);
}

function rmpos_client_ip(): string
{
    $remote = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
    return substr(trim($remote), 0, 45);
}

function rmpos_json_response(int $statusCode, array $payload): never
{
    http_response_code($statusCode);
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store, no-cache, must-revalidate');
    header('X-Content-Type-Options: nosniff');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
