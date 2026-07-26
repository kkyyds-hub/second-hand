-- DEVELOPMENT ONLY: this script permanently removes every object and row in secondhand2.
-- Run with a MySQL administrator account. Do not use it against production.

SELECT 'WARNING: rebuilding development database secondhand2; all existing data will be deleted.' AS warning;
DROP DATABASE IF EXISTS `secondhand2`;
CREATE DATABASE `secondhand2`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE `secondhand2`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';
SET sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE TABLE `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `mobile` VARCHAR(32) NOT NULL,
  `nickname` VARCHAR(64) NOT NULL,
  `bio` VARCHAR(500) NULL,
  `email` VARCHAR(128) NULL,
  `avatar` VARCHAR(500) NULL,
  `credit_score` INT NOT NULL DEFAULT 100,
  `credit_level` VARCHAR(16) NOT NULL DEFAULT 'lv3',
  `credit_updated_at` DATETIME NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'active',
  `is_seller` TINYINT NOT NULL DEFAULT 0,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_mobile` (`mobile`),
  UNIQUE KEY `uk_users_email` (`email`),
  INDEX `idx_users_status_created` (`status`, `create_time`),
  CONSTRAINT `chk_users_status` CHECK (`status` IN ('active', 'banned', 'disabled')),
  CONSTRAINT `chk_users_is_seller` CHECK (`is_seller` IN (0, 1)),
  CONSTRAINT `chk_users_is_deleted` CHECK (`is_deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `addresses` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `receiver_name` VARCHAR(64) NOT NULL,
  `mobile` VARCHAR(32) NOT NULL,
  `province_code` VARCHAR(32) NULL,
  `province_name` VARCHAR(64) NOT NULL,
  `city_code` VARCHAR(32) NULL,
  `city_name` VARCHAR(64) NOT NULL,
  `district_code` VARCHAR(32) NULL,
  `district_name` VARCHAR(64) NOT NULL,
  `detail_address` VARCHAR(255) NOT NULL,
  `is_default` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_addr_user_default_updated` (`user_id`, `is_default`, `updated_at`, `id`),
  CONSTRAINT `fk_addresses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_addresses_default` CHECK (`is_default` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_oauth_bind` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `provider` VARCHAR(32) NOT NULL,
  `external_id` VARCHAR(128) NOT NULL,
  `bind_status` TINYINT NOT NULL DEFAULT 1,
  `last_login_time` DATETIME NULL,
  `remark` VARCHAR(255) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_provider_external` (`provider`, `external_id`),
  INDEX `idx_oauth_user_status` (`user_id`, `bind_status`),
  CONSTRAINT `fk_oauth_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_oauth_bind_status` CHECK (`bind_status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_credit_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `delta` INT NOT NULL,
  `reason_type` VARCHAR(64) NOT NULL,
  `reason_note` VARCHAR(500) NULL,
  `ref_id` BIGINT NULL,
  `score_before` INT NOT NULL,
  `score_after` INT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_credit_logs_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_credit_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_violations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `biz_id` BIGINT NULL,
  `violation_type` VARCHAR(64) NOT NULL,
  `description` VARCHAR(1000) NOT NULL,
  `evidence` TEXT NULL,
  `punish` VARCHAR(255) NULL,
  `credit` INT NOT NULL DEFAULT 0,
  `record_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_violations_user_time` (`user_id`, `record_time`),
  INDEX `idx_user_violations_type_time` (`violation_type`, `record_time`),
  CONSTRAINT `fk_user_violations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_bans` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `ban_type` VARCHAR(16) NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `source` VARCHAR(32) NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NULL,
  `created_by` BIGINT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_bans_user_end` (`user_id`, `end_time`),
  CONSTRAINT `fk_user_bans_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_bans_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_user_bans_type` CHECK (`ban_type` IN ('TEMP', 'PERM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `products` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT NOT NULL,
  `price` DECIMAL(12, 2) NOT NULL,
  `images` TEXT NULL,
  `category` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'under_review',
  `view_count` INT NOT NULL DEFAULT 0,
  `reason` VARCHAR(500) NULL,
  `owner_id` BIGINT NOT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_products_owner` (`owner_id`, `create_time`),
  INDEX `idx_products_status` (`status`, `create_time`),
  INDEX `idx_products_category` (`category`, `status`, `create_time`),
  FULLTEXT KEY `ft_title_desc_ngram` (`title`, `description`),
  CONSTRAINT `fk_products_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_products_status` CHECK (`status` IN ('under_review', 'on_sale', 'off_shelf', 'sold')),
  CONSTRAINT `chk_products_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_products_deleted` CHECK (`is_deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `favorites` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorites_user_product` (`user_id`, `product_id`),
  INDEX `idx_favorites_user_time` (`user_id`, `create_time`),
  INDEX `idx_favorites_product` (`product_id`),
  CONSTRAINT `fk_favorites_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_favorites_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_favorites_deleted` CHECK (`is_deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `cart_items` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_product` (`user_id`, `product_id`),
  INDEX `idx_cart_user_time` (`user_id`, `create_time`),
  INDEX `idx_cart_product` (`product_id`),
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(64) NOT NULL,
  `buyer_id` BIGINT NOT NULL,
  `seller_id` BIGINT NOT NULL,
  `total_amount` DECIMAL(12, 2) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `shipping_address` VARCHAR(1000) NOT NULL,
  `shipping_company` VARCHAR(64) NULL,
  `tracking_no` VARCHAR(128) NULL,
  `shipping_remark` VARCHAR(500) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time` DATETIME NULL,
  `ship_time` DATETIME NULL,
  `complete_time` DATETIME NULL,
  `cancel_time` DATETIME NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cancel_reason` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  INDEX `idx_orders_buyer_status` (`buyer_id`, `status`, `create_time`),
  INDEX `idx_orders_seller_status` (`seller_id`, `status`, `create_time`),
  INDEX `idx_orders_status_create_time` (`status`, `create_time`),
  CONSTRAINT `fk_orders_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_orders_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_orders_status` CHECK (`status` IN ('pending', 'paid', 'shipped', 'completed', 'cancelled')),
  CONSTRAINT `chk_orders_amount` CHECK (`total_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_items` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `price` DECIMAL(12, 2) NOT NULL,
  `quantity` INT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_order_items_product` (`product_id`),
  CONSTRAINT `fk_order_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_order_items_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_order_items_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `reviews` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `buyer_id` BIGINT NOT NULL,
  `seller_id` BIGINT NOT NULL,
  `role` TINYINT NOT NULL,
  `rating` TINYINT NOT NULL,
  `content` VARCHAR(1000) NULL,
  `is_anonymous` TINYINT NOT NULL DEFAULT 0,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_order_role` (`order_id`, `role`),
  INDEX `idx_product_time` (`product_id`, `create_time`),
  INDEX `idx_seller_time` (`seller_id`, `create_time`),
  INDEX `idx_buyer_time` (`buyer_id`, `create_time`),
  CONSTRAINT `fk_reviews_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reviews_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reviews_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reviews_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_reviews_role` CHECK (`role` IN (1, 2)),
  CONSTRAINT `chk_reviews_rating` CHECK (`rating` BETWEEN 1 AND 5),
  CONSTRAINT `chk_reviews_anonymous` CHECK (`is_anonymous` IN (0, 1)),
  CONSTRAINT `chk_reviews_deleted` CHECK (`is_deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_report_ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_no` VARCHAR(64) NOT NULL,
  `product_id` BIGINT NOT NULL,
  `reporter_id` BIGINT NOT NULL,
  `report_type` VARCHAR(64) NOT NULL,
  `description` VARCHAR(1000) NOT NULL,
  `evidence_urls` TEXT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `resolver_id` BIGINT NULL,
  `resolve_action` VARCHAR(64) NULL,
  `resolve_remark` VARCHAR(1000) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  INDEX `idx_report_status_time` (`status`, `create_time`),
  INDEX `idx_report_product` (`product_id`),
  CONSTRAINT `fk_report_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_report_resolver` FOREIGN KEY (`resolver_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_report_status` CHECK (`status` IN ('PENDING', 'RESOLVED', 'REJECTED')),
  CONSTRAINT `chk_report_action` CHECK (`resolve_action` IS NULL OR `resolve_action` IN ('NONE', 'WARN', 'OFF_SHELF', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_violations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL,
  `violation_type` VARCHAR(64) NOT NULL,
  `description` VARCHAR(1000) NOT NULL,
  `evidence_urls` TEXT NULL,
  `punishment_result` VARCHAR(500) NULL,
  `credit_score_change` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'active',
  `record_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_pv_product_status_id` (`product_id`, `status`, `id`),
  CONSTRAINT `fk_product_violations_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_product_violations_status` CHECK (`status` IN ('active', 'closed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_status_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL,
  `action` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT NULL,
  `operator_role` VARCHAR(32) NOT NULL,
  `before_status` VARCHAR(32) NULL,
  `after_status` VARCHAR(32) NULL,
  `reason_code` VARCHAR(64) NULL,
  `reason_text` VARCHAR(1000) NULL,
  `extra_json` JSON NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_psal_product_time` (`product_id`, `create_time`),
  INDEX `idx_psal_operator_time` (`operator_id`, `create_time`),
  INDEX `idx_psal_action_time` (`action`, `create_time`),
  CONSTRAINT `fk_status_audit_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_status_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `after_sales` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `buyer_id` BIGINT NOT NULL,
  `seller_id` BIGINT NOT NULL,
  `reason` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `seller_remark` VARCHAR(1000) NULL,
  `platform_remark` VARCHAR(1000) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_order` (`order_id`),
  INDEX `idx_after_sales_buyer_status` (`buyer_id`, `status`, `create_time`),
  INDEX `idx_after_sales_seller_status` (`seller_id`, `status`, `create_time`),
  CONSTRAINT `fk_after_sales_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_after_sales_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_after_sales_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_after_sales_status` CHECK (`status` IN ('APPLIED', 'SELLER_APPROVED', 'SELLER_REJECTED', 'DISPUTED', 'PLATFORM_APPROVED', 'PLATFORM_REJECTED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `after_sale_evidences` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `after_sale_id` BIGINT NOT NULL,
  `image_url` VARCHAR(500) NOT NULL,
  `sort` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_evidence_sort` (`after_sale_id`, `sort`),
  CONSTRAINT `fk_after_sale_evidences_after_sale` FOREIGN KEY (`after_sale_id`) REFERENCES `after_sales` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_after_sale_evidence_sort` CHECK (`sort` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_flags` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `type` VARCHAR(64) NOT NULL,
  `remark` VARCHAR(1000) NULL,
  `created_by` BIGINT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_flag_type` (`order_id`, `type`),
  INDEX `idx_order_flags_time` (`create_time`),
  CONSTRAINT `fk_order_flags_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_order_flags_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_ship_timeout_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `deadline_time` DATETIME NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NULL,
  `last_error` VARCHAR(1000) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ship_timeout_order_id` (`order_id`),
  INDEX `idx_ship_timeout_status_deadline` (`status`, `deadline_time`, `id`),
  INDEX `idx_ship_timeout_next_retry` (`next_retry_time`, `id`),
  CONSTRAINT `fk_ship_timeout_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_ship_timeout_status` CHECK (`status` IN ('PENDING', 'DONE', 'CANCELLED')),
  CONSTRAINT `chk_ship_timeout_retry` CHECK (`retry_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_ship_reminder_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `seller_id` BIGINT NOT NULL,
  `level` VARCHAR(8) NOT NULL,
  `deadline_time` DATETIME NOT NULL,
  `remind_time` DATETIME NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT NOT NULL DEFAULT 0,
  `running_at` DATETIME NULL,
  `sent_at` DATETIME NULL,
  `client_msg_id` VARCHAR(128) NULL,
  `last_error` VARCHAR(1000) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_level` (`order_id`, `level`),
  UNIQUE KEY `uk_ship_reminder_client_msg` (`client_msg_id`),
  INDEX `idx_status_remind_time` (`status`, `remind_time`, `id`),
  INDEX `idx_status_running_at` (`status`, `running_at`, `id`),
  INDEX `idx_seller_status` (`seller_id`, `status`, `remind_time`),
  CONSTRAINT `fk_ship_reminder_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_ship_reminder_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_ship_reminder_level` CHECK (`level` IN ('H24', 'H6', 'H1')),
  CONSTRAINT `chk_ship_reminder_status` CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
  CONSTRAINT `chk_ship_reminder_retry` CHECK (`retry_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_refund_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `refund_type` VARCHAR(32) NOT NULL,
  `amount` DECIMAL(12, 2) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `idempotency_key` VARCHAR(128) NOT NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NULL,
  `fail_reason` VARCHAR(1000) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_order_type` (`order_id`, `refund_type`),
  UNIQUE KEY `uk_refund_idempotency` (`idempotency_key`),
  INDEX `idx_refund_status_time` (`status`, `create_time`, `id`),
  INDEX `idx_refund_next_retry` (`status`, `next_retry_time`, `id`),
  CONSTRAINT `fk_refund_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_refund_status` CHECK (`status` IN ('PENDING', 'SUCCESS', 'FAILED')),
  CONSTRAINT `chk_refund_amount` CHECK (`amount` >= 0),
  CONSTRAINT `chk_refund_retry` CHECK (`retry_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_wallets` (
  `user_id` BIGINT NOT NULL,
  `balance` DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_wallets_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `wallet_transactions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(64) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  `amount` DECIMAL(14, 2) NOT NULL,
  `balance_after` DECIMAL(14, 2) NOT NULL,
  `remark` VARCHAR(500) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_tx_biz_type_biz_id` (`biz_type`, `biz_id`),
  INDEX `idx_wallet_transactions_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_wallet_transactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `withdraw_requests` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `amount` DECIMAL(14, 2) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'APPLIED',
  `bank_card_no` VARCHAR(128) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_withdraw_user_time` (`user_id`, `create_time`),
  INDEX `idx_withdraw_status_time` (`status`, `create_time`),
  CONSTRAINT `fk_withdraw_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_withdraw_status` CHECK (`status` IN ('APPLIED', 'APPROVED', 'REJECTED', 'PAID')),
  CONSTRAINT `chk_withdraw_amount` CHECK (`amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `points_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(64) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  `points` INT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_user_biz` (`user_id`, `biz_type`, `biz_id`),
  INDEX `idx_points_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_points_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `message_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(128) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `exchange_name` VARCHAR(128) NOT NULL,
  `routing_key` VARCHAR(128) NOT NULL,
  `biz_id` BIGINT NOT NULL,
  `payload_json` JSON NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'NEW',
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  INDEX `idx_status_time` (`status`, `created_at`, `id`),
  INDEX `idx_outbox_status_retry_id` (`status`, `next_retry_time`, `id`),
  INDEX `idx_outbox_biz_type` (`biz_id`, `event_type`),
  CONSTRAINT `chk_outbox_status` CHECK (`status` IN ('NEW', 'SENT', 'FAIL')),
  CONSTRAINT `chk_outbox_retry` CHECK (`retry_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mq_consume_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `consumer` VARCHAR(128) NOT NULL,
  `event_id` VARCHAR(128) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'OK',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_event` (`consumer`, `event_id`),
  INDEX `idx_event` (`event_id`),
  CONSTRAINT `chk_consume_log_status` CHECK (`status` IN ('OK', 'FAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;

INSERT INTO `users` (`id`, `username`, `password`, `mobile`, `nickname`, `bio`, `email`, `avatar`, `credit_score`, `credit_level`, `credit_updated_at`, `status`, `is_seller`, `is_deleted`, `create_time`, `update_time`) VALUES
  (1, 'admin_demo', '$2a$10$LNXFcetmbZHFUW0xTklRV.a1.Fgli0LcsU6OqC0wV7Cg7O1E5Fex2', '13900000001', 'Dev Administrator', 'Development administrator account.', 'admin@example.test', '/uploads/avatars/admin.png', 100, 'lv3', '2026-07-23 09:00:00', 'active', 0, 0, '2026-07-23 09:00:00', '2026-07-23 09:00:00'),
  (2, 'buyer_demo', '$2a$10$JmLVcL0aYfpS4rqHzHcelOZkCxJSncZhaHXrOEF2XbdOoEqGE.mqy', '13800000001', 'Dev Buyer', 'Development buyer account.', 'buyer@example.test', '/uploads/avatars/buyer.png', 108, 'lv4', '2026-07-22 10:00:00', 'active', 0, 0, '2026-07-20 09:00:00', '2026-07-22 10:00:00'),
  (3, 'seller_demo', '$2a$10$JmLVcL0aYfpS4rqHzHcelOZkCxJSncZhaHXrOEF2XbdOoEqGE.mqy', '13700000001', 'Dev Seller', 'Development seller account.', 'seller@example.test', '/uploads/avatars/seller.png', 96, 'lv3', '2026-07-21 10:00:00', 'active', 1, 0, '2026-07-20 09:00:00', '2026-07-21 10:00:00'),
  (4, 'restricted_demo', '$2a$10$JmLVcL0aYfpS4rqHzHcelOZkCxJSncZhaHXrOEF2XbdOoEqGE.mqy', '13600000001', 'Restricted Demo', 'Development governance fixture.', 'restricted@example.test', NULL, 70, 'lv2', '2026-07-21 10:00:00', 'banned', 0, 0, '2026-07-20 09:00:00', '2026-07-21 10:00:00');

INSERT INTO `addresses` (`id`, `user_id`, `receiver_name`, `mobile`, `province_code`, `province_name`, `city_code`, `city_name`, `district_code`, `district_name`, `detail_address`, `is_default`, `created_at`, `updated_at`) VALUES
  (1, 2, 'Dev Buyer', '13800000001', '310000', 'Shanghai', '310100', 'Shanghai', '310115', 'Pudong', '88 Example Road, Room 501', 1, '2026-07-20 09:10:00', '2026-07-20 09:10:00'),
  (2, 3, 'Dev Seller', '13700000001', '330000', 'Zhejiang', '330100', 'Hangzhou', '330106', 'Xihu', '18 Sample Street, Room 302', 1, '2026-07-20 09:10:00', '2026-07-20 09:10:00');

INSERT INTO `user_oauth_bind` (`id`, `user_id`, `provider`, `external_id`, `bind_status`, `last_login_time`, `remark`, `create_time`, `update_time`) VALUES
  (1, 2, 'github', 'dev-buyer-github-001', 1, '2026-07-22 10:00:00', 'Development OAuth fixture.', '2026-07-20 09:20:00', '2026-07-22 10:00:00');

INSERT INTO `user_credit_logs` (`id`, `user_id`, `delta`, `reason_type`, `reason_note`, `ref_id`, `score_before`, `score_after`, `create_time`) VALUES
  (1, 2, 8, 'order_completed', 'Completed demo order.', 1004, 100, 108, '2026-07-22 10:00:00'),
  (2, 3, -4, 'product_violation', 'Demo policy warning.', 102, 100, 96, '2026-07-21 10:00:00');

INSERT INTO `user_violations` (`id`, `user_id`, `biz_id`, `violation_type`, `description`, `evidence`, `punish`, `credit`, `record_time`, `create_time`) VALUES
  (1, 4, 1005, 'abusive_cancel', 'Development-only cancelled-order risk record.', 'https://example.test/evidence/user-violation-1.png', 'Temporary suspension', -30, '2026-07-21 11:00:00', '2026-07-21 11:00:00');

INSERT INTO `user_bans` (`id`, `user_id`, `ban_type`, `reason`, `source`, `start_time`, `end_time`, `created_by`, `create_time`) VALUES
  (1, 4, 'TEMP', 'Development governance fixture.', 'ADMIN', '2026-07-21 11:05:00', '2026-08-21 11:05:00', 1, '2026-07-21 11:05:00');

INSERT INTO `products` (`id`, `title`, `description`, `price`, `images`, `category`, `status`, `view_count`, `reason`, `owner_id`, `is_deleted`, `create_time`, `update_time`) VALUES
  (101, 'On-sale mechanical keyboard', 'A fictional hot-swappable keyboard for product list and order fixtures.', 199.00, 'https://example.test/images/keyboard-1.png', 'digital', 'on_sale', 56, NULL, 3, 0, '2026-07-20 10:00:00', '2026-07-23 09:00:00'),
  (102, 'Review sample desk lamp', 'A fictional desk lamp waiting for moderation.', 49.00, 'https://example.test/images/lamp-1.png', 'home', 'under_review', 3, 'Awaiting development moderation fixture.', 3, 0, '2026-07-21 10:00:00', '2026-07-21 10:00:00'),
  (103, 'Off-shelf reference book', 'A fictional book retained for off-shelf queries.', 35.00, 'https://example.test/images/book-1.png', 'books', 'off_shelf', 12, 'Seller paused listing.', 3, 0, '2026-07-19 10:00:00', '2026-07-22 10:00:00'),
  (104, 'Sold graphics tablet', 'A fictional tablet used by completed order fixtures.', 399.00, 'https://example.test/images/tablet-1.png', 'digital', 'sold', 89, 'Sold through completed development order.', 3, 0, '2026-07-18 10:00:00', '2026-07-22 10:00:00'),
  (105, 'Cancelled order headphones', 'A fictional listing used by cancellation fixtures.', 129.00, 'https://example.test/images/headphones-1.png', 'digital', 'on_sale', 7, NULL, 3, 0, '2026-07-20 10:00:00', '2026-07-20 10:00:00');

INSERT INTO `favorites` (`id`, `user_id`, `product_id`, `is_deleted`, `create_time`, `update_time`) VALUES
  (1, 2, 101, 0, '2026-07-22 09:00:00', '2026-07-22 09:00:00');

INSERT INTO `orders` (`id`, `order_no`, `buyer_id`, `seller_id`, `total_amount`, `status`, `shipping_address`, `shipping_company`, `tracking_no`, `shipping_remark`, `create_time`, `pay_time`, `ship_time`, `complete_time`, `cancel_time`, `update_time`, `cancel_reason`) VALUES
  (1001, 'DEV-ORDER-PENDING-001', 2, 3, 199.00, 'pending', 'Dev Buyer, 13800000001, 88 Example Road, Room 501', NULL, NULL, NULL, NOW(), NULL, NULL, NULL, NULL, NOW(), NULL),
  (1002, 'DEV-ORDER-PAID-001', 2, 3, 49.00, 'paid', 'Dev Buyer, 13800000001, 88 Example Road, Room 501', NULL, NULL, 'Prepare demo shipment.', DATE_SUB(NOW(), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 1 MINUTE), NULL, NULL, NULL, NOW(), NULL),
  (1003, 'DEV-ORDER-SHIPPED-001', 2, 3, 35.00, 'shipped', 'Dev Buyer, 13800000001, 88 Example Road, Room 501', 'Mock Express', 'MOCK-TRACK-1003', 'Shipped for after-sale fixture.', '2026-07-21 08:00:00', '2026-07-21 08:10:00', '2026-07-21 09:00:00', NULL, NULL, '2026-07-21 09:00:00', NULL),
  (1004, 'DEV-ORDER-COMPLETED-001', 2, 3, 399.00, 'completed', 'Dev Buyer, 13800000001, 88 Example Road, Room 501', 'Mock Express', 'MOCK-TRACK-1004', 'Completed review fixture.', '2026-07-20 08:00:00', '2026-07-20 08:10:00', '2026-07-20 09:00:00', '2026-07-22 10:00:00', NULL, '2026-07-22 10:00:00', NULL),
  (1005, 'DEV-ORDER-CANCELLED-001', 4, 3, 129.00, 'cancelled', 'Restricted Demo, 13600000001, 99 Sample Street', NULL, NULL, NULL, '2026-07-20 08:00:00', NULL, NULL, NULL, '2026-07-20 08:20:00', '2026-07-20 08:20:00', 'buyer_cancel');

INSERT INTO `order_items` (`id`, `order_id`, `product_id`, `price`, `quantity`, `create_time`) VALUES
  (1, 1001, 101, 199.00, 1, '2026-07-23 08:00:00'),
  (2, 1002, 102, 49.00, 1, '2026-07-22 08:00:00'),
  (3, 1003, 103, 35.00, 1, '2026-07-21 08:00:00'),
  (4, 1004, 104, 399.00, 1, '2026-07-20 08:00:00'),
  (5, 1005, 105, 129.00, 1, '2026-07-20 08:00:00');

INSERT INTO `reviews` (`id`, `order_id`, `product_id`, `buyer_id`, `seller_id`, `role`, `rating`, `content`, `is_anonymous`, `is_deleted`, `create_time`, `update_time`) VALUES
  (1, 1004, 104, 2, 3, 1, 5, 'Accurate description and fast development delivery.', 0, 0, '2026-07-22 10:10:00', '2026-07-22 10:10:00'),
  (2, 1004, 104, 2, 3, 2, 5, 'Responsive buyer fixture.', 0, 0, '2026-07-22 10:20:00', '2026-07-22 10:20:00');

INSERT INTO `product_report_ticket` (`id`, `ticket_no`, `product_id`, `reporter_id`, `report_type`, `description`, `evidence_urls`, `status`, `resolver_id`, `resolve_action`, `resolve_remark`, `create_time`, `update_time`) VALUES
  (1, 'DEV-REPORT-0001', 102, 2, 'misleading_description', 'Development report fixture.', '["https://example.test/evidence/report-1.png"]', 'RESOLVED', 1, 'WARN', 'Seller warned in development fixture.', '2026-07-21 12:00:00', '2026-07-21 12:30:00');

INSERT INTO `product_violations` (`id`, `product_id`, `violation_type`, `description`, `evidence_urls`, `punishment_result`, `credit_score_change`, `status`, `record_time`) VALUES
  (1, 102, 'metadata_incomplete', 'Development product policy fixture.', '["https://example.test/evidence/product-violation-1.png"]', 'Seller warning', -4, 'active', '2026-07-21 12:30:00');

INSERT INTO `product_status_audit_log` (`id`, `product_id`, `action`, `operator_id`, `operator_role`, `before_status`, `after_status`, `reason_code`, `reason_text`, `extra_json`, `create_time`) VALUES
  (1, 102, 'REPORT_RESOLVED', 1, 'admin', 'under_review', 'under_review', 'metadata_incomplete', 'Report retained as development fixture.', JSON_OBJECT('reportTicketNo', 'DEV-REPORT-0001'), '2026-07-21 12:30:00'),
  (2, 104, 'ORDER_COMPLETED', NULL, 'system', 'on_sale', 'sold', 'order_completed', 'Completed development order.', JSON_OBJECT('orderNo', 'DEV-ORDER-COMPLETED-001'), '2026-07-22 10:00:00');

INSERT INTO `after_sales` (`id`, `order_id`, `buyer_id`, `seller_id`, `reason`, `status`, `seller_remark`, `platform_remark`, `create_time`, `update_time`) VALUES
  (1, 1003, 2, 3, 'Development after-sale quality check.', 'APPLIED', NULL, NULL, '2026-07-22 13:00:00', '2026-07-22 13:00:00');

INSERT INTO `after_sale_evidences` (`id`, `after_sale_id`, `image_url`, `sort`, `create_time`) VALUES
  (1, 1, 'https://example.test/evidence/after-sale-1.png', 0, '2026-07-22 13:01:00'),
  (2, 1, 'https://example.test/evidence/after-sale-2.png', 1, '2026-07-22 13:02:00');

INSERT INTO `order_flags` (`id`, `order_id`, `type`, `remark`, `created_by`, `create_time`) VALUES
  (1, 1005, 'suspicious', 'Development cancelled-order risk flag.', 1, '2026-07-20 08:25:00');

INSERT INTO `order_ship_timeout_task` (`id`, `order_id`, `deadline_time`, `status`, `retry_count`, `next_retry_time`, `last_error`, `create_time`, `update_time`) VALUES
  (1, 1002, DATE_ADD(NOW(), INTERVAL 48 HOUR), 'PENDING', 0, NULL, NULL, NOW(), NOW()),
  (2, 1003, '2026-07-23 08:10:00', 'CANCELLED', 0, NULL, 'Cancelled after shipment.', '2026-07-21 08:10:00', '2026-07-21 09:00:00');

INSERT INTO `order_ship_reminder_task` (`id`, `order_id`, `seller_id`, `level`, `deadline_time`, `remind_time`, `status`, `retry_count`, `running_at`, `sent_at`, `client_msg_id`, `last_error`, `create_time`, `update_time`) VALUES
  (1, 1002, 3, 'H24', DATE_ADD(NOW(), INTERVAL 48 HOUR), DATE_ADD(NOW(), INTERVAL 24 HOUR), 'PENDING', 0, NULL, NULL, 'DEV-REMIND-1002-H24', NULL, NOW(), NOW()),
  (2, 1002, 3, 'H6', DATE_ADD(NOW(), INTERVAL 48 HOUR), DATE_ADD(NOW(), INTERVAL 42 HOUR), 'PENDING', 0, NULL, NULL, 'DEV-REMIND-1002-H6', NULL, NOW(), NOW()),
  (3, 1002, 3, 'H1', DATE_ADD(NOW(), INTERVAL 48 HOUR), DATE_ADD(NOW(), INTERVAL 47 HOUR), 'PENDING', 0, NULL, NULL, 'DEV-REMIND-1002-H1', NULL, NOW(), NOW());

INSERT INTO `order_refund_task` (`id`, `order_id`, `refund_type`, `amount`, `status`, `idempotency_key`, `retry_count`, `next_retry_time`, `fail_reason`, `create_time`, `update_time`) VALUES
  (1, 1005, 'ship_timeout', 129.00, 'SUCCESS', 'DEV-REFUND-1005-SHIP-TIMEOUT', 0, NULL, NULL, '2026-07-20 08:25:00', '2026-07-20 08:30:00');

INSERT INTO `user_wallets` (`user_id`, `balance`, `update_time`) VALUES
  (2, 500.00, '2026-07-22 10:00:00'),
  (3, 399.00, '2026-07-22 10:00:00'),
  (4, 0.00, '2026-07-20 08:30:00');

INSERT INTO `wallet_transactions` (`id`, `user_id`, `biz_type`, `biz_id`, `amount`, `balance_after`, `remark`, `create_time`) VALUES
  (1, 2, 'ORDER_REFUND', 1005, 129.00, 500.00, 'Development cancelled-order refund.', '2026-07-20 08:30:00'),
  (2, 3, 'ORDER_INCOME', 1004, 399.00, 399.00, 'Development completed-order income.', '2026-07-22 10:00:00'),
  (3, 2, 'WITHDRAW', 1, -50.00, 450.00, 'Development withdrawal request.', '2026-07-22 11:00:00');

INSERT INTO `withdraw_requests` (`id`, `user_id`, `amount`, `status`, `bank_card_no`, `create_time`, `update_time`) VALUES
  (1, 2, 50.00, 'APPLIED', '6222********1234', '2026-07-22 11:00:00', '2026-07-22 11:00:00');

INSERT INTO `points_ledger` (`id`, `user_id`, `biz_type`, `biz_id`, `points`, `create_time`) VALUES
  (1, 2, 'ORDER_COMPLETED', 1004, 8, '2026-07-22 10:00:00');

INSERT INTO `message_outbox` (`id`, `event_id`, `event_type`, `exchange_name`, `routing_key`, `biz_id`, `payload_json`, `status`, `retry_count`, `next_retry_time`, `created_at`, `updated_at`) VALUES
  (1, 'DEV-EVENT-ORDER-CREATED-1001', 'ORDER_CREATED', 'order.events.exchange', 'order.created', 1001, JSON_OBJECT('orderId', 1001, 'orderNo', 'DEV-ORDER-PENDING-001'), 'SENT', 0, NULL, '2026-07-23 08:00:00', '2026-07-23 08:00:01'),
  (2, 'DEV-EVENT-ORDER-PAID-1002', 'ORDER_PAID', 'order.events.exchange', 'order.paid', 1002, JSON_OBJECT('orderId', 1002, 'orderNo', 'DEV-ORDER-PAID-001'), 'NEW', 0, NULL, '2026-07-22 08:10:00', '2026-07-22 08:10:00'),
  (3, 'DEV-EVENT-ORDER-CANCELLED-1005', 'ORDER_CANCELLED', 'order.events.exchange', 'order.cancelled', 1005, JSON_OBJECT('orderId', 1005, 'reason', 'buyer_cancel'), 'FAIL', 1, '2026-07-23 12:00:00', '2026-07-20 08:20:00', '2026-07-20 08:25:00');

INSERT INTO `mq_consume_log` (`id`, `consumer`, `event_id`, `status`, `created_at`, `updated_at`) VALUES
  (1, 'OrderPaidConsumer', 'DEV-EVENT-ORDER-PAID-1002', 'OK', '2026-07-22 08:10:01', '2026-07-22 08:10:01'),
  (2, 'OrderCreatedConsumer', 'DEV-EVENT-ORDER-CREATED-1001', 'OK', '2026-07-23 08:00:01', '2026-07-23 08:00:01');

COMMIT;

SELECT 'Development rebuild complete.' AS result;
