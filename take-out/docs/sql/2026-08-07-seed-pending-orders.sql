-- 订单一期联调种子：用户 + 地址 + 待接单订单 + 明细
-- 在 take-out 目录执行（容器名以 docker compose ps 为准，常见 take-out-mysql）：
--   docker cp docs/sql/2026-08-07-seed-pending-orders.sql take-out-mysql:/tmp/seed-orders.sql
--   docker exec -i take-out-mysql mysql -uroot -proot --default-character-set=utf8mb4 take_out -e "SOURCE /tmp/seed-orders.sql"
--
-- status=2 待接单，pay_status=1 已支付（模拟用户已付完进入待接单）

USE take_out;

-- 可重复执行：先清本脚本造的数据
DELETE FROM order_detail WHERE order_id IN (1001, 1002, 1003);
DELETE FROM orders WHERE id IN (1001, 1002, 1003);
DELETE FROM address_book WHERE id = 1001;
DELETE FROM user WHERE id = 1001;

INSERT INTO `user` (`id`, `openid`, `name`, `phone`, `sex`, `avatar`, `create_time`)
VALUES (1001, 'mock_openid_cyrus_001', '测试用户小明', '13800138000', '1', NULL, NOW());

INSERT INTO `address_book` (
  `id`, `user_id`, `consignee`, `sex`, `phone`,
  `province_name`, `city_name`, `district_name`, `detail`, `label`, `is_default`
) VALUES (
  1001, 1001, '小明', '1', '13800138000',
  '北京市', '北京市', '海淀区', '中关村大街1号', '公司', 1
);

-- 订单1：待接单，含两道菜
INSERT INTO `orders` (
  `id`, `number`, `status`, `user_id`, `address_book_id`,
  `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`,
  `remark`, `phone`, `address`, `user_name`, `consignee`,
  `estimated_delivery_time`, `delivery_status`, `pack_amount`,
  `tableware_number`, `tableware_status`
) VALUES (
  1001, 'ACCT-000048', 2, 1001, 1001,
  NOW(), NOW(), 1, 1, 62.00,
  '少放辣', '13800138000', '北京市海淀区中关村大街1号', '测试用户小明', '小明',
  DATE_ADD(NOW(), INTERVAL 45 MINUTE), 1, 2,
  2, 1
);

INSERT INTO `order_detail` (`name`, `image`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`)
VALUES
('老坛酸菜鱼', NULL, 1001, 51, NULL, '微辣', 1, 56.00),
('米饭', NULL, 1001, 49, NULL, NULL, 1, 2.00),
('王老吉', NULL, 1001, 46, NULL, NULL, 1, 6.00);

-- 订单2：待接单
INSERT INTO `orders` (
  `id`, `number`, `status`, `user_id`, `address_book_id`,
  `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`,
  `remark`, `phone`, `address`, `user_name`, `consignee`,
  `estimated_delivery_time`, `delivery_status`, `pack_amount`,
  `tableware_number`, `tableware_status`
) VALUES (
  1002, 'ORD20260807120002', 2, 1001, 1001,
  DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 10 MINUTE), 1, 1, 10.00,
  NULL, '13800138000', '北京市海淀区中关村大街1号', '测试用户小明', '小明',
  DATE_ADD(NOW(), INTERVAL 40 MINUTE), 1, 1,
  1, 1
);

INSERT INTO `order_detail` (`name`, `image`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`)
VALUES
('北冰洋', NULL, 1002, 47, NULL, NULL, 1, 4.00),
('米饭', NULL, 1002, 49, NULL, NULL, 2, 2.00);

-- 订单3：待接单（再造一笔方便测统计角标）
INSERT INTO `orders` (
  `id`, `number`, `status`, `user_id`, `address_book_id`,
  `order_time`, `checkout_time`, `pay_method`, `pay_status`, `amount`,
  `remark`, `phone`, `address`, `user_name`, `consignee`,
  `estimated_delivery_time`, `delivery_status`, `pack_amount`,
  `tableware_number`, `tableware_status`
) VALUES (
  1003, 'ORD20260807120003', 2, 1001, 1001,
  DATE_SUB(NOW(), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 5 MINUTE), 1, 1, 6.00,
  '尽快送达', '13900139000', '北京市海淀区中关村大街1号', '测试用户小明', '小明',
  DATE_ADD(NOW(), INTERVAL 35 MINUTE), 1, 1,
  1, 1
);

INSERT INTO `order_detail` (`name`, `image`, `order_id`, `dish_id`, `setmeal_id`, `dish_flavor`, `number`, `amount`)
VALUES
('王老吉', NULL, 1003, 46, NULL, NULL, 1, 6.00);
