<?php
declare(strict_types=1);

/**
 * RM.POS License Administration Panel for Hostinger.
 * Protected by bcrypt/Argon2id password hash from rmpos-private/config.php,
 * hardened session cookies, and CSRF tokens.
 */

require_once dirname(__DIR__) . '/api/bootstrap.php';
require_once dirname(__DIR__) . '/api/crypto.php';

header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: same-origin');
header('Cache-Control: no-store, no-cache, must-revalidate');

$isHttps = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
    || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');

session_set_cookie_params([
    'lifetime' => 0,
    'path' => '/admin',
    'secure' => $isHttps,
    'httponly' => true,
    'samesite' => 'Strict',
]);
session_start();

function h(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf_token']) || !is_string($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf_token'];
}

function verify_csrf(): bool
{
    $posted = (string) ($_POST['csrf_token'] ?? '');
    $expected = (string) ($_SESSION['csrf_token'] ?? '');
    return $posted !== '' && $expected !== '' && hash_equals($expected, $posted);
}

$bootError = '';
$config = [];
$pdo = null;

try {
    $config = rmpos_load_config();
    $pdo = rmpos_db($config);
} catch (Throwable $e) {
    $bootError = 'ملف الإعدادات الخاص غير مهيّأ بعد خارج public_html (راجع DEPLOY_HOSTINGER.md).';
}

$adminUser = (string) ($config['admin_username'] ?? 'admin');
$adminHash = (string) ($config['admin_password_hash'] ?? '');
$errorMsg = '';
$successMsg = '';
$generatedToken = '';
$generatedTitle = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && $bootError === '') {
    if (!verify_csrf()) {
        $errorMsg = 'انتهت صلاحية الجلسة الأمنية (CSRF). أعد المحاولة.';
    } else {
        $action = (string) ($_POST['action'] ?? '');

        if ($action === 'login') {
            $u = trim((string) ($_POST['username'] ?? ''));
            $p = (string) ($_POST['password'] ?? '');
            if ($adminHash === '') {
                $errorMsg = 'يجب تعيين admin_password_hash أولاً في rmpos-private/config.php.';
            } elseif (hash_equals($adminUser, $u) && password_verify($p, $adminHash)) {
                session_regenerate_id(true);
                $_SESSION['rmpos_admin_authed'] = true;
                $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
            } else {
                usleep(400000);
                $errorMsg = 'اسم المستخدم أو كلمة المرور غير صحيحة.';
            }
        } elseif ($action === 'logout') {
            $_SESSION = [];
            session_destroy();
            header('Location: index.php');
            exit;
        } elseif (!empty($_SESSION['rmpos_admin_authed']) && $pdo instanceof PDO) {
            try {
                if ($action === 'issue_license') {
                    $deviceId = rmpos_normalize_device_id((string) ($_POST['device_id'] ?? ''));
                    $customerName = trim((string) ($_POST['customer_name'] ?? ''));
                    $phone = trim((string) ($_POST['phone'] ?? ''));
                    $edition = strtoupper(trim((string) ($_POST['edition'] ?? 'PRO')));
                    $days = max(1, min(3650, (int) ($_POST['days'] ?? 365)));
                    $graceDays = max(1, min($days, (int) ($_POST['grace_days'] ?? 30)));

                    if (!rmpos_is_valid_device_id($deviceId)) {
                        throw new InvalidArgumentException('معرّف الجهاز يجب أن يتكوّن من 16 حرفاً سداسياً (0-9 و A-F).');
                    }
                    if (!in_array($edition, ['STANDARD', 'PRO', 'ENTERPRISE'], true)) {
                        $edition = 'PRO';
                    }

                    $nowMs = rmpos_now_ms();
                    $expiresMs = $nowMs + ($days * 86400 * 1000);

                    $stmt = $pdo->prepare(
                        'INSERT INTO licenses (device_id, customer_name, phone, edition, status, revocation_reason, issued_at_ms, expires_at_ms, grace_days)
                         VALUES (:device_id, :customer_name, :phone, :edition, "ACTIVE", "", :issued_at, :expires_at, :grace_days)
                         ON DUPLICATE KEY UPDATE
                           customer_name = VALUES(customer_name),
                           phone = VALUES(phone),
                           edition = VALUES(edition),
                           status = "ACTIVE",
                           revocation_reason = "",
                           issued_at_ms = VALUES(issued_at_ms),
                           expires_at_ms = VALUES(expires_at_ms),
                           grace_days = VALUES(grace_days)'
                    );
                    $stmt->execute([
                        ':device_id' => $deviceId,
                        ':customer_name' => $customerName,
                        ':phone' => $phone,
                        ':edition' => $edition,
                        ':issued_at' => $nowMs,
                        ':expires_at' => $expiresMs,
                        ':grace_days' => $graceDays,
                    ]);

                    $generatedToken = rmpos_sign_license_token($deviceId, $edition, $expiresMs, $graceDays, $config, $nowMs);
                    $generatedTitle = "رمز التفعيل الموقّع للجهاز {$deviceId} ({$edition} - {$days} يوم)";
                    $successMsg = 'تم حفظ الاشتراك وتوليد رمز التفعيل الموقّع بنجاح.';
                } elseif ($action === 'revoke_device') {
                    $deviceId = rmpos_normalize_device_id((string) ($_POST['device_id'] ?? ''));
                    $reason = trim((string) ($_POST['reason'] ?? 'تم إيقاف الاشتراك من الإدارة'));
                    if (!rmpos_is_valid_device_id($deviceId)) {
                        throw new InvalidArgumentException('معرّف جهاز غير صالح.');
                    }
                    $stmt = $pdo->prepare('UPDATE licenses SET status = "REVOKED", revocation_reason = :reason WHERE device_id = :device_id');
                    $stmt->execute([':reason' => $reason, ':device_id' => $deviceId]);
                    $successMsg = "تم إيقاف الجهاز {$deviceId}؛ سيستلم التطبيق رمز إلغاء موقّع عند أول اتصال.";
                } elseif ($action === 'reactivate_device') {
                    $deviceId = rmpos_normalize_device_id((string) ($_POST['device_id'] ?? ''));
                    if (!rmpos_is_valid_device_id($deviceId)) {
                        throw new InvalidArgumentException('معرّف جهاز غير صالح.');
                    }
                    $stmt = $pdo->prepare('UPDATE licenses SET status = "ACTIVE", revocation_reason = "" WHERE device_id = :device_id');
                    $stmt->execute([':device_id' => $deviceId]);
                    $successMsg = "تم إعادة تفعيل الجهاز {$deviceId} بنجاح.";
                } elseif ($action === 'sign_clock_reset') {
                    $deviceId = rmpos_normalize_device_id((string) ($_POST['device_id'] ?? ''));
                    $hours = max(1, min(168, (int) ($_POST['hours'] ?? 24)));
                    if (!rmpos_is_valid_device_id($deviceId)) {
                        throw new InvalidArgumentException('معرّف جهاز غير صالح.');
                    }
                    $generatedToken = rmpos_sign_clock_reset_token($deviceId, $hours, $config);
                    $generatedTitle = "رمز إعادة ضبط الساعة (CLOCK_RESET) للجهاز {$deviceId}";
                    $successMsg = 'تم توليد رمز إعادة ضبط الساعة لمرة واحدة.';
                } elseif ($action === 'sign_recovery') {
                    $deviceId = rmpos_normalize_device_id((string) ($_POST['device_id'] ?? ''));
                    $targetUser = trim((string) ($_POST['target_user'] ?? 'admin'));
                    $hours = max(1, min(168, (int) ($_POST['hours'] ?? 24)));
                    if (!rmpos_is_valid_device_id($deviceId)) {
                        throw new InvalidArgumentException('معرّف جهاز غير صالح.');
                    }
                    $generatedToken = rmpos_sign_recovery_token($deviceId, $targetUser, $hours, $config);
                    $generatedTitle = "رمز الاسترجاع الطارئ (RECOVERY) للجهاز {$deviceId} للمستخدم {$targetUser}";
                    $successMsg = 'تم توليد رمز الاسترجاع الطارئ لمرة واحدة.';
                }
            } catch (Throwable $e) {
                $errorMsg = $e->getMessage();
            }
        }
    }
}

$isAuthed = !empty($_SESSION['rmpos_admin_authed']) && $bootError === '';
$licenses = [];
if ($isAuthed && $pdo instanceof PDO) {
    $licenses = $pdo->query('SELECT * FROM licenses ORDER BY updated_at DESC LIMIT 200')->fetchAll();
}
$csrf = csrf_token();
?>
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>لوحة تراخيص RM.POS</title>
  <style>
    :root { --bg: #0f172a; --card: #1e293b; --border: #334155; --text: #f8fafc; --muted: #94a3b8; --primary: #38bdf8; --ok: #22c55e; --danger: #ef4444; --warn: #f59e0b; }
    * { box-sizing: border-box; font-family: system-ui, -apple-system, "Segoe UI", Tahoma, sans-serif; }
    body { margin: 0; background: var(--bg); color: var(--text); padding: 20px; }
    .container { max-width: 1120px; margin: 0 auto; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 20px; margin-bottom: 18px; }
    h1, h2 { margin-top: 0; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
    label { display: block; font-size: 13px; color: var(--muted); margin-bottom: 4px; }
    input, select, textarea { width: 100%; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--border); background: #0f172a; color: var(--text); font-size: 14px; }
    button { cursor: pointer; border: 0; border-radius: 8px; padding: 10px 16px; font-weight: 700; font-size: 14px; }
    .btn-primary { background: var(--primary); color: #0f172a; }
    .btn-danger { background: var(--danger); color: #fff; }
    .btn-ok { background: var(--ok); color: #052e16; }
    .btn-warn { background: var(--warn); color: #1c1917; }
    .alert { padding: 12px 16px; border-radius: 10px; margin-bottom: 14px; font-size: 14px; }
    .alert-err { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--danger); color: #fecaca; }
    .alert-ok { background: rgba(34, 197, 94, 0.15); border: 1px solid var(--ok); color: #bbf7d0; }
    .token-box { background: #020617; border: 1px solid var(--primary); padding: 14px; border-radius: 10px; word-break: break-all; font-family: monospace; font-size: 12.5px; direction: ltr; text-align: left; }
    table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
    th, td { border-bottom: 1px solid var(--border); padding: 10px 8px; text-align: right; }
    th { color: var(--muted); font-weight: 600; }
    .badge { padding: 3px 8px; border-radius: 999px; font-size: 12px; font-weight: 700; }
    .badge-active { background: rgba(34, 197, 94, 0.2); color: #86efac; }
    .badge-revoked { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }
    .row-actions { display: flex; gap: 6px; flex-wrap: wrap; }
    .topbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
  </style>
</head>
<body>
<div class="container">
  <div class="topbar">
    <h1>لوحة إدارة تراخيص RM.POS</h1>
    <?php if ($isAuthed): ?>
      <form method="post">
        <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
        <input type="hidden" name="action" value="logout" />
        <button class="btn-danger" type="submit">تسجيل الخروج</button>
      </form>
    <?php endif; ?>
  </div>

  <?php if ($bootError !== ''): ?>
    <div class="alert alert-err"><?= h($bootError) ?></div>
  <?php endif; ?>
  <?php if ($errorMsg !== ''): ?>
    <div class="alert alert-err"><?= h($errorMsg) ?></div>
  <?php endif; ?>
  <?php if ($successMsg !== ''): ?>
    <div class="alert alert-ok"><?= h($successMsg) ?></div>
  <?php endif; ?>

  <?php if (!$isAuthed): ?>
    <div class="card" style="max-width:420px;margin:40px auto;">
      <h2>تسجيل دخول المدير</h2>
      <form method="post">
        <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
        <input type="hidden" name="action" value="login" />
        <div style="margin-bottom:12px;">
          <label>اسم المستخدم</label>
          <input name="username" required autocomplete="username" />
        </div>
        <div style="margin-bottom:16px;">
          <label>كلمة المرور</label>
          <input name="password" type="password" required autocomplete="current-password" />
        </div>
        <button class="btn-primary" style="width:100%;" type="submit">دخول</button>
      </form>
    </div>
  <?php else: ?>
    <?php if ($generatedToken !== ''): ?>
      <div class="card">
        <h2><?= h($generatedTitle) ?></h2>
        <div id="generatedToken" class="token-box"><?= h($generatedToken) ?></div>
        <div style="margin-top:10px;">
          <button class="btn-primary" type="button" onclick="navigator.clipboard.writeText(document.getElementById('generatedToken').innerText)">نسخ الرمز</button>
        </div>
      </div>
    <?php endif; ?>

    <div class="card">
      <h2>إصدار أو تجديد ترخيص جهاز</h2>
      <form method="post">
        <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
        <input type="hidden" name="action" value="issue_license" />
        <div class="grid">
          <div>
            <label>معرّف الجهاز (Device ID - 16 HEX)</label>
            <input name="device_id" placeholder="4F3A8B1C9D2E0F5A" maxlength="16" required style="direction:ltr;text-align:left;" />
          </div>
          <div>
            <label>اسم الزبون / المتجر</label>
            <input name="customer_name" placeholder="متجر الأمل" />
          </div>
          <div>
            <label>الهاتف</label>
            <input name="phone" placeholder="0600000000" style="direction:ltr;text-align:left;" />
          </div>
          <div>
            <label>النسخة</label>
            <select name="edition">
              <option value="PRO" selected>PRO</option>
              <option value="ENTERPRISE">ENTERPRISE</option>
              <option value="STANDARD">STANDARD</option>
            </select>
          </div>
          <div>
            <label>مدة الاشتراك (أيام)</label>
            <input name="days" type="number" min="1" max="3650" value="365" required />
          </div>
          <div>
            <label>مهلة العمل دون اتصال (أيام)</label>
            <input name="grace_days" type="number" min="1" max="365" value="30" required />
          </div>
        </div>
        <div style="margin-top:14px;">
          <button class="btn-primary" type="submit">حفظ وتوليد رمز التفعيل</button>
        </div>
      </form>
    </div>

    <div class="card">
      <h2>رموز الطوارئ لمرة واحدة (CLOCK_RESET / RECOVERY)</h2>
      <div class="grid">
        <form method="post" style="border:1px solid var(--border);border-radius:10px;padding:12px;">
          <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
          <input type="hidden" name="action" value="sign_clock_reset" />
          <label>معرّف الجهاز لإعادة ضبط الساعة</label>
          <input name="device_id" maxlength="16" required style="direction:ltr;text-align:left;margin-bottom:8px;" />
          <label>صلاحية الرمز (ساعات)</label>
          <input name="hours" type="number" min="1" max="168" value="24" style="margin-bottom:10px;" />
          <button class="btn-warn" type="submit">توليد رمز CLOCK_RESET</button>
        </form>
        <form method="post" style="border:1px solid var(--border);border-radius:10px;padding:12px;">
          <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
          <input type="hidden" name="action" value="sign_recovery" />
          <label>معرّف الجهاز للاسترجاع الطارئ</label>
          <input name="device_id" maxlength="16" required style="direction:ltr;text-align:left;margin-bottom:8px;" />
          <label>اسم المستخدم المستهدف</label>
          <input name="target_user" value="admin" style="margin-bottom:8px;" />
          <label>صلاحية الرمز (ساعات)</label>
          <input name="hours" type="number" min="1" max="168" value="24" style="margin-bottom:10px;" />
          <button class="btn-warn" type="submit">توليد رمز RECOVERY</button>
        </form>
      </div>
    </div>

    <div class="card">
      <h2>الأجهزة المسجّلة (<?= count($licenses) ?>)</h2>
      <div style="overflow-x:auto;">
        <table>
          <thead>
            <tr>
              <th>Device ID</th>
              <th>الزبون</th>
              <th>النسخة</th>
              <th>الحالة</th>
              <th>تاريخ الانتهاء</th>
              <th>آخر اتصال</th>
              <th>إجراءات</th>
            </tr>
          </thead>
          <tbody>
          <?php foreach ($licenses as $row): ?>
            <?php
              $expSec = (int) floor(((int) $row['expires_at_ms']) / 1000);
              $expDate = gmdate('Y-m-d', $expSec);
              $isRevoked = ($row['status'] ?? '') === 'REVOKED';
            ?>
            <tr>
              <td style="font-family:monospace;direction:ltr;"><?= h((string) $row['device_id']) ?></td>
              <td><?= h((string) $row['customer_name']) ?><br><small style="color:var(--muted);"><?= h((string) $row['phone']) ?></small></td>
              <td><?= h((string) $row['edition']) ?></td>
              <td>
                <span class="badge <?= $isRevoked ? 'badge-revoked' : 'badge-active' ?>">
                  <?= $isRevoked ? 'موقوف' : 'نشط' ?>
                </span>
              </td>
              <td><?= h($expDate) ?></td>
              <td><?= h((string) ($row['last_seen_at'] ?? '—')) ?></td>
              <td>
                <div class="row-actions">
                  <?php if (!$isRevoked): ?>
                    <form method="post" onsubmit="return confirm('تأكيد إيقاف هذا الجهاز؟');">
                      <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
                      <input type="hidden" name="action" value="revoke_device" />
                      <input type="hidden" name="device_id" value="<?= h((string) $row['device_id']) ?>" />
                      <input type="hidden" name="reason" value="تم إيقاف الاشتراك من لوحة الإدارة" />
                      <button class="btn-danger" type="submit">إيقاف</button>
                    </form>
                  <?php else: ?>
                    <form method="post">
                      <input type="hidden" name="csrf_token" value="<?= h($csrf) ?>" />
                      <input type="hidden" name="action" value="reactivate_device" />
                      <input type="hidden" name="device_id" value="<?= h((string) $row['device_id']) ?>" />
                      <button class="btn-ok" type="submit">إعادة تفعيل</button>
                    </form>
                  <?php endif; ?>
                </div>
              </td>
            </tr>
          <?php endforeach; ?>
          </tbody>
        </table>
      </div>
    </div>
  <?php endif; ?>
</div>
</body>
</html>
