-- RM.POS License Server & Verification Database Schema for Hostinger MySQL (MariaDB / MySQL 8+)
-- Import this file via Hostinger hPanel -> Databases -> phpMyAdmin

CREATE TABLE IF NOT EXISTS `licenses` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `device_id` VARCHAR(16) NOT NULL,
  `customer_name` VARCHAR(160) NOT NULL DEFAULT '',
  `phone` VARCHAR(40) NOT NULL DEFAULT '',
  `edition` ENUM('STANDARD', 'PRO', 'ENTERPRISE') NOT NULL DEFAULT 'PRO',
  `status` ENUM('ACTIVE', 'REVOKED') NOT NULL DEFAULT 'ACTIVE',
  `revocation_reason` VARCHAR(255) NOT NULL DEFAULT '',
  `issued_at_ms` BIGINT UNSIGNED NOT NULL,
  `expires_at_ms` BIGINT UNSIGNED NOT NULL,
  `grace_days` INT UNSIGNED NOT NULL DEFAULT 30,
  `last_seen_at` TIMESTAMP NULL DEFAULT NULL,
  `last_ip` VARCHAR(45) NOT NULL DEFAULT '',
  `notes` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_device_id` (`device_id`),
  KEY `idx_status_expires` (`status`, `expires_at_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `verification_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `device_id` VARCHAR(16) NOT NULL,
  `outcome` VARCHAR(40) NOT NULL,
  `client_ip` VARCHAR(45) NOT NULL DEFAULT '',
  `client_timestamp_ms` BIGINT UNSIGNED NULL DEFAULT NULL,
  `server_time_ms` BIGINT UNSIGNED NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_created` (`device_id`, `created_at`),
  KEY `idx_ip_created` (`client_ip`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
