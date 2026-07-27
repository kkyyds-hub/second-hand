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
