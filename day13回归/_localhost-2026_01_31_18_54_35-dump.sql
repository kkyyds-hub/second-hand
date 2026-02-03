-- MySQL dump 10.13  Distrib 9.4.0, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: secondhand2
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `addresses`
--

DROP TABLE IF EXISTS `addresses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `addresses` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Address ID',
  `user_id` bigint NOT NULL COMMENT 'User ID who owns the address',
  `receiver_name` varchar(50) NOT NULL COMMENT 'Receiver name',
  `mobile` varchar(20) NOT NULL COMMENT 'Receiver mobile',
  `province_code` varchar(10) NOT NULL COMMENT 'Province code',
  `province_name` varchar(50) NOT NULL COMMENT 'Province name',
  `city_code` varchar(10) NOT NULL COMMENT 'City code',
  `city_name` varchar(50) NOT NULL COMMENT 'City name',
  `district_code` varchar(10) NOT NULL COMMENT 'District code',
  `district_name` varchar(50) NOT NULL COMMENT 'District name',
  `detail_address` varchar(200) NOT NULL COMMENT 'Detail address (street, number)',
  `is_default` tinyint(1) DEFAULT '0' COMMENT 'Is this the default address?',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Address creation time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last updated time',
  PRIMARY KEY (`id`),
  KEY `fk_addresses_user` (`user_id`),
  CONSTRAINT `fk_addresses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=700004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User address table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `addresses`
--

LOCK TABLES `addresses` WRITE;
/*!40000 ALTER TABLE `addresses` DISABLE KEYS */;
INSERT INTO `addresses` VALUES (1,1,'张三','13800000001','110000','北京市','110100','北京市','110101','东城区','东华门街道 1 号',1,'2025-12-03 21:46:44','2025-12-03 21:46:44'),(2,1,'张三(公司)','13800000001','310000','上海市','310100','上海市','310115','浦东新区','世纪大道 100 号',0,'2025-12-03 21:46:44','2025-12-03 21:46:44'),(3,2,'李四','13800000002','440000','广东省','440100','广州市','440106','天河区','体育西路 18 号',1,'2025-12-03 21:46:44','2025-12-03 21:46:44'),(700001,1,'回归买家-默认','13800000001','110000','北京市','110100','北京市','110101','东城区','回归测试路 1 号',1,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(700002,1,'回归买家-备用','13800000001','310000','上海市','310100','上海市','310115','浦东新区','回归测试路 2 号',0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(700003,2,'回归卖家-地址','13800000002','440000','广东省','440100','广州市','440106','天河区','回归卖家路 66 号',1,'2026-01-02 02:02:22','2026-01-02 02:02:22');
/*!40000 ALTER TABLE `addresses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `favorites`
--

DROP TABLE IF EXISTS `favorites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Favorite ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `product_id` bigint NOT NULL COMMENT 'Product ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete: 0=normal, 1=deleted',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorites_user_product` (`user_id`,`product_id`),
  KEY `idx_favorites_user_time` (`user_id`,`create_time`),
  KEY `idx_favorites_product` (`product_id`),
  CONSTRAINT `fk_favorites_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_favorites_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `favorites`
--

LOCK TABLES `favorites` WRITE;
/*!40000 ALTER TABLE `favorites` DISABLE KEYS */;
/*!40000 ALTER TABLE `favorites` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Order item ID',
  `order_id` bigint NOT NULL COMMENT 'Associated order ID',
  `product_id` bigint NOT NULL COMMENT 'Product ID at purchase time',
  `price` decimal(10,2) NOT NULL COMMENT 'Snapshot price',
  `quantity` int NOT NULL DEFAULT '1' COMMENT 'Purchased quantity',
  `product_title_snapshot` varchar(120) DEFAULT NULL COMMENT '下单时的商品标题',
  `product_thumbnail_snapshot` varchar(255) DEFAULT NULL COMMENT '下单时的商品缩略图 URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  KEY `fk_items_order` (`order_id`),
  KEY `fk_items_product` (`product_id`),
  CONSTRAINT `fk_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=910001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_items`
--

LOCK TABLES `order_items` WRITE;
/*!40000 ALTER TABLE `order_items` DISABLE KEYS */;
INSERT INTO `order_items` VALUES (1,1,1,1999.00,1,'九成新 iPad 2021 款','https://img.example.com/product/ipad01-1.jpg','2025-12-24 21:46:10','2025-12-24 21:46:10'),(2,2,3,499.00,1,'低价处理 二手手机','https://img.example.com/product/phone01.jpg','2025-12-24 21:46:10','2025-12-24 21:46:10'),(3,4,4,88.88,1,NULL,NULL,'2025-12-24 21:52:38','2025-12-24 21:52:38'),(4,5,7,199.00,1,NULL,NULL,'2025-12-30 21:22:50','2025-12-30 21:22:50'),(5,6,7,199.00,1,NULL,NULL,'2025-12-30 21:26:10','2025-12-30 21:26:10'),(6,7,7,199.00,1,NULL,NULL,'2025-12-30 21:26:46','2025-12-30 21:26:46'),(10,11,9,899.00,1,NULL,NULL,'2026-01-02 01:09:33','2026-01-02 01:09:33'),(11,12,8,299.00,1,NULL,NULL,'2026-01-02 01:15:13','2026-01-02 01:15:13'),(12,13,8,299.00,1,NULL,NULL,'2026-01-02 01:25:23','2026-01-02 01:25:23'),(13,14,8,299.00,1,NULL,NULL,'2026-01-02 01:30:58','2026-01-02 01:30:58'),(810001,800001,900401,101.00,1,'FX-订单专用-待支付',NULL,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(810002,800002,900402,102.00,1,'FX-订单专用-已支付',NULL,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(810003,800003,900403,103.00,1,'FX-订单专用-已发货',NULL,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(810004,800004,900404,104.00,1,'FX-订单专用-已完成',NULL,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(810005,800005,900405,105.00,1,'FX-订单专用-已取消',NULL,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(810006,800006,10001,199.00,1,NULL,NULL,'2026-01-08 21:28:40','2026-01-08 21:28:40'),(810007,800007,900001,199.00,1,NULL,NULL,'2026-01-08 22:12:20','2026-01-08 22:12:20'),(810008,800008,900002,299.00,1,NULL,NULL,'2026-01-08 22:12:22','2026-01-08 22:12:22'),(810009,800009,900002,299.00,1,NULL,NULL,'2026-01-08 22:12:22','2026-01-08 22:12:22'),(810010,800010,900003,899.00,1,NULL,NULL,'2026-01-08 22:12:42','2026-01-08 22:12:42'),(810011,800011,900004,39.90,1,NULL,NULL,'2026-01-08 22:12:44','2026-01-08 22:12:44'),(810012,800012,900004,39.90,1,NULL,NULL,'2026-01-08 22:12:44','2026-01-08 22:12:44'),(810013,800013,900005,59.00,1,NULL,NULL,'2026-01-08 22:29:57','2026-01-08 22:29:57'),(810014,800014,900006,129.00,1,NULL,NULL,'2026-01-08 22:29:58','2026-01-08 22:29:58'),(810015,800015,900006,129.00,1,NULL,NULL,'2026-01-08 22:29:58','2026-01-08 22:29:58'),(910000,900030,910000,99.90,1,'Day12验收商品-请勿删除',NULL,'2026-01-28 00:57:41','2026-01-28 00:57:41');
/*!40000 ALTER TABLE `order_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Order ID',
  `order_no` varchar(64) NOT NULL COMMENT 'Order number',
  `buyer_id` bigint NOT NULL COMMENT 'Buyer user ID',
  `seller_id` bigint NOT NULL COMMENT 'Seller user ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT 'Total amount',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending | paid | shipped | completed | cancelled',
  `shipping_address` varchar(255) DEFAULT NULL COMMENT 'Shipping address snapshot',
  `shipping_company` varchar(50) DEFAULT NULL COMMENT '物流公司，如 SF、YTO、ZTO 等',
  `tracking_no` varchar(64) DEFAULT NULL COMMENT '物流单号',
  `shipping_remark` varchar(255) DEFAULT NULL COMMENT '发货备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `pay_time` datetime DEFAULT NULL COMMENT 'Paid at',
  `complete_time` datetime DEFAULT NULL COMMENT 'Completed at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `cancel_time` datetime DEFAULT NULL COMMENT 'Canceled at',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT 'Cancel reason',
  `ship_time` datetime DEFAULT NULL COMMENT 'Shipped at',
  `receive_time` datetime DEFAULT NULL COMMENT 'Received/confirmed at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  KEY `fk_orders_buyer` (`buyer_id`),
  KEY `fk_orders_seller` (`seller_id`),
  KEY `idx_orders_status_create_time` (`status`,`create_time`),
  CONSTRAINT `fk_orders_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_orders_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=900031 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'202512030001',1,2,1999.00,'completed','北京市 东城区 东华门街道 1 号（张三 13800000001）','顺丰','SF1234567890',NULL,'2025-12-03 22:10:00','2025-12-03 22:11:00','2025-12-04 10:20:00','2025-12-04 10:20:00',NULL,NULL,NULL,NULL),(2,'202512030002',1,2,499.00,'completed','上海市 浦东新区 世纪大道 100 号（张三 13800000001）','SF','SF1234567890',NULL,'2025-12-05 09:30:00','2025-12-24 21:46:50','2025-12-24 22:17:24','2025-12-24 22:17:24',NULL,NULL,NULL,NULL),(4,'2025122421523816662',1,2,88.88,'cancelled','测试买家01 13800000001 北京市北京市东城区 回归测试路1号',NULL,NULL,NULL,'2025-12-24 21:52:38',NULL,NULL,'2025-12-30 21:17:03','2025-12-30 21:17:03','timeout',NULL,NULL),(5,'2025123021225012714',1,2,199.00,'cancelled','回归收货地址：张三 13800000001 北京市东城区 回归测试路1号',NULL,NULL,NULL,'2025-12-30 21:22:50',NULL,NULL,'2025-12-30 21:23:26','2025-12-30 21:23:26','buyer_cancel_test',NULL,NULL),(6,'2025123021261014821',1,2,199.00,'cancelled','回归收货地址：张三 13800000001 北京市东城区 回归测试路1号',NULL,NULL,NULL,'2025-12-30 21:26:10',NULL,NULL,'2025-12-30 21:26:10','2025-12-30 21:26:10','buyer_cancel_test',NULL,NULL),(7,'2025123021264612380',1,2,199.00,'completed','回归收货地址：张三 13800000001 北京市东城区 回归测试路1号','SF','SF123456',NULL,'2025-12-30 21:26:46','2025-12-30 21:26:46','2025-12-30 21:26:47','2025-12-30 21:26:47',NULL,NULL,NULL,NULL),(11,'2026010201093319542',1,2,899.00,'cancelled','广东省广州市天河区XX路1号',NULL,NULL,NULL,'2026-01-02 01:09:33',NULL,NULL,'2026-01-02 01:25:22','2026-01-02 01:25:22','timeout',NULL,NULL),(12,'2026010201151317091',1,2,299.00,'cancelled','广东省广州市天河区XX路1号',NULL,NULL,NULL,'2026-01-02 01:15:13',NULL,NULL,'2026-01-02 01:30:22','2026-01-02 01:30:22','timeout',NULL,NULL),(13,'2026010201252313746',1,2,299.00,'cancelled','广东省广州市天河区XX路1号',NULL,NULL,NULL,'2026-01-02 01:25:23',NULL,NULL,'2026-01-02 01:41:22','2026-01-02 01:41:22','timeout',NULL,NULL),(14,'2026010201305816336',1,2,299.00,'completed','广东省广州市天河区XX路1号','SF','SF1767288658874','放门口/电话联系','2026-01-02 01:30:58','2026-01-02 01:30:58','2026-01-02 01:30:59','2026-01-02 01:30:59',NULL,NULL,'2026-01-02 01:30:58',NULL),(800001,'FX202601010001',1,2,101.00,'cancelled','夹具地址：回归测试路1号（13800000001）',NULL,NULL,NULL,'2025-12-31 02:02:22',NULL,NULL,'2026-01-02 02:02:22','2026-01-02 02:02:22','timeout',NULL,NULL),(800002,'FX202601010002',1,2,102.00,'paid','夹具地址：回归测试路1号（13800000001）',NULL,NULL,NULL,'2025-12-31 02:02:22','2025-12-31 02:02:22',NULL,'2026-01-02 02:02:22',NULL,NULL,NULL,NULL),(800003,'FX202601010003',1,2,103.00,'shipped','夹具地址：回归测试路1号（13800000001）','SF','SF-FIX-0001','夹具发货备注','2025-12-31 02:02:22','2025-12-31 02:02:22',NULL,'2026-01-02 02:02:22',NULL,NULL,'2026-01-01 02:02:22',NULL),(800004,'FX202601010004',1,2,104.00,'completed','夹具地址：回归测试路1号（13800000001）','SF','SF-FIX-0002',NULL,'2025-12-30 02:02:22','2025-12-30 02:02:22','2026-01-01 02:02:22','2026-01-02 02:02:22',NULL,NULL,'2025-12-31 02:02:22','2026-01-01 02:02:22'),(800005,'FX202601010005',1,2,105.00,'cancelled','夹具地址：回归测试路1号（13800000001）',NULL,NULL,NULL,'2025-12-30 02:02:22',NULL,NULL,'2026-01-02 02:02:22','2025-12-30 02:02:22','fixture_cancel',NULL,NULL),(800006,'2026010821284012948',1,2,199.00,'completed','北京市北京市东城区回归测试路 1 号','SF','SF1234567890','测试发货备注','2026-01-08 21:28:40','2026-01-08 21:28:41','2026-01-08 21:28:41','2026-01-08 21:28:41',NULL,NULL,'2026-01-08 21:28:41',NULL),(800007,'2026010822122014483',1,2,199.00,'completed','北京市北京市东城区回归测试路 1 号','SF','SF1234567890','测试发货备注','2026-01-08 22:12:20','2026-01-08 22:12:21','2026-01-08 22:12:21','2026-01-08 22:12:21',NULL,NULL,'2026-01-08 22:12:21',NULL),(800008,'2026010822122219631',1,2,299.00,'cancelled','北京市北京市东城区回归测试路 1 号',NULL,NULL,NULL,'2026-01-08 22:12:22',NULL,NULL,'2026-01-08 22:12:22','2026-01-08 22:12:22','buyer_cancel',NULL,NULL),(800009,'2026010822122219410',1,2,299.00,'paid','北京市北京市东城区回归测试路 1 号',NULL,NULL,NULL,'2026-01-08 22:12:22','2026-01-08 22:12:22',NULL,'2026-01-08 22:12:22',NULL,NULL,NULL,NULL),(800010,'2026010822124212459',1,2,899.00,'completed','北京市北京市东城区回归测试路 1 号','SF','SF1234567890','测试发货备注','2026-01-08 22:12:42','2026-01-08 22:12:42','2026-01-08 22:12:43','2026-01-08 22:12:43',NULL,NULL,'2026-01-08 22:12:43',NULL),(800011,'2026010822124412974',1,2,39.90,'cancelled','北京市北京市东城区回归测试路 1 号',NULL,NULL,NULL,'2026-01-08 22:12:44',NULL,NULL,'2026-01-08 22:12:44','2026-01-08 22:12:44','buyer_cancel',NULL,NULL),(800012,'2026010822124418477',1,2,39.90,'paid','北京市北京市东城区回归测试路 1 号',NULL,NULL,NULL,'2026-01-08 22:12:44','2026-01-08 22:12:44',NULL,'2026-01-08 22:12:44',NULL,NULL,NULL,NULL),(800013,'2026010822295717598',1,2,59.00,'completed','北京市北京市东城区回归测试路 1 号','SF','SF9876543210','幂等性测试','2026-01-08 22:29:57','2026-01-08 22:29:57','2026-01-08 22:29:58','2026-01-08 22:29:58',NULL,NULL,'2026-01-08 22:29:58',NULL),(800014,'2026010822295819063',1,2,129.00,'cancelled','北京市北京市东城区回归测试路 1 号',NULL,NULL,NULL,'2026-01-08 22:29:58',NULL,NULL,'2026-01-08 22:29:58','2026-01-08 22:29:58','buyer_cancel',NULL,NULL),(800015,'2026010822295815431',1,2,129.00,'paid','北京市北京市东城区回归测试路 1 号',NULL,NULL,NULL,'2026-01-08 22:29:58','2026-01-08 22:29:58',NULL,'2026-01-08 22:29:58',NULL,NULL,NULL,NULL),(900030,'ORD-DAY12-900030',1,2,99.90,'completed','Day12夹具地址',NULL,NULL,NULL,'2026-01-28 00:57:41','2026-01-28 00:57:41','2026-01-28 00:57:41','2026-01-28 00:57:41',NULL,NULL,'2026-01-28 00:57:41','2026-01-28 00:57:41');
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_violations`
--

DROP TABLE IF EXISTS `product_violations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_violations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Product violation ID',
  `product_id` bigint NOT NULL COMMENT 'Product ID',
  `violation_type` varchar(40) NOT NULL COMMENT 'Violation type',
  `description` text COMMENT 'Violation description',
  `evidence_urls` text COMMENT 'Evidence URLs',
  `punishment_result` varchar(120) DEFAULT NULL COMMENT 'Punishment result',
  `credit_score_change` int DEFAULT NULL COMMENT 'Credit score change',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active | inactive',
  `record_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record time',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pv_product` (`product_id`),
  CONSTRAINT `fk_pv_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=900002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品违规记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_violations`
--

LOCK TABLES `product_violations` WRITE;
/*!40000 ALTER TABLE `product_violations` DISABLE KEYS */;
INSERT INTO `product_violations` VALUES (1,3,'fake_description','商品描述存在明显不实信息，用户举报后核实属实。','聊天记录截图、验机报告截图','下架商品，信用扣 20 分',-20,'active','2025-12-20 23:20:06','2025-11-28 21:46:44','2025-11-28 21:46:44'),(900001,900201,'fixture_violation','夹具预置：用于验证违规记录查询/展示口径','N/A','下架商品',-10,'active','2026-01-02 02:02:22','2026-01-02 02:02:22','2026-01-02 02:02:22');
/*!40000 ALTER TABLE `product_violations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Product ID',
  `owner_id` bigint NOT NULL COMMENT 'Owner user ID (who published the product)',
  `title` varchar(120) NOT NULL COMMENT 'Product title',
  `description` text COMMENT 'Product description',
  `price` decimal(10,2) NOT NULL COMMENT 'Price',
  `images` text COMMENT 'Comma-separated image URLs',
  `category` varchar(60) DEFAULT NULL COMMENT 'Category label',
  `status` varchar(20) NOT NULL DEFAULT 'under_review' COMMENT 'under_review | on_sale | sold | off_shelf',
  `view_count` int NOT NULL DEFAULT '0' COMMENT 'View count',
  `reason` varchar(255) DEFAULT NULL COMMENT 'Off-shelf or rejection reason',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete: 0=normal, 1=deleted',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  KEY `idx_products_owner` (`owner_id`),
  KEY `idx_products_status` (`status`),
  KEY `idx_products_category` (`category`),
  FULLTEXT KEY `ft_title_desc_ngram` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `fk_products_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=910001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='二手商品信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (1,2,'九成新 iPad 2021 款','自用 iPad 2021 款，128G，几乎无划痕，带原装充电器和保护壳。',1999.00,'https://img.example.com/product/ipad01-1.jpg,https://img.example.com/product/ipad01-2.jpg','电子数码','sold',123,NULL,0,'2025-12-03 21:46:44','2025-12-24 19:32:52'),(2,2,'全新未拆封 蓝牙耳机','礼物重复，不想要了，全新未拆封，支持当面验货。',299.00,'https://img.example.com/product/earphone01.jpg','电子数码','under_review',0,NULL,0,'2025-12-03 21:46:44','2025-12-21 21:45:25'),(3,2,'低价处理 二手手机','存在进水维修记录，仅适合当备用机使用，具体情况详聊。',499.00,'https://img.example.com/product/phone01.jpg','电子数码','sold',45,'涉嫌描述不实，已被平台下架',0,'2025-12-03 21:46:44','2025-12-24 19:32:52'),(4,2,'Day3回归在售商品','用于 Day3 回归：市场列表/详情/创建订单',88.88,'https://img.example.com/product/day3-onsale-1.jpg','回归测试','on_sale',0,NULL,0,'2025-12-24 19:32:52','2025-12-30 21:17:03'),(5,2,'Day3回归待审核商品','用于 Day3 回归：admin 待审核列表/审核通过',77.77,'https://img.example.com/product/day3-review-1.jpg','回归测试','under_review',0,NULL,0,'2025-12-24 19:32:52','2025-12-24 19:32:52'),(6,2,'Day3回归下架商品','用于 Day2/Day3 回归：状态=off_shelf 的筛选',66.66,'https://img.example.com/product/day3-offshelf-1.jpg','回归测试','off_shelf',0,'回归预置下架',0,'2025-12-24 19:32:52','2025-12-24 19:32:52'),(7,2,'回归在售商品A-耳机','Day3 回归用：在售商品A',199.00,NULL,'数码','sold',0,NULL,0,'2025-12-24 21:46:26','2025-12-30 21:26:46'),(8,2,'回归在售商品B-键盘','Day3 回归用：在售商品B',299.00,NULL,'数码','on_sale',0,NULL,0,'2025-12-24 21:46:26','2026-01-02 01:37:19'),(9,2,'回归在售商品C-显示器','Day3 回归用：在售商品C',899.00,NULL,'数码','on_sale',0,NULL,0,'2025-12-24 21:46:26','2026-01-02 01:25:22'),(10001,2,'D6_ON_SALE','Day6 固定：在售（用于负向用例）',199.00,'https://img.example.com/d6/onsale.jpg','回归测试','sold',0,NULL,0,'2026-01-04 20:35:32','2026-01-08 21:28:40'),(10002,2,'D6_UNDER_REVIEW','Day6 固定：审核中（用于 withdraw）',88.00,'https://img.example.com/d6/review.jpg','回归测试','off_shelf',0,'seller_withdraw',0,'2026-01-04 20:35:32','2026-01-04 20:51:43'),(10003,2,'D6_OFF_SHELF_RJ','Day6 固定：下架/驳回（用于 resubmit）',66.00,'https://img.example.com/d6/off1.jpg','回归测试','under_review',0,NULL,0,'2026-01-04 20:35:32','2026-01-04 20:51:44'),(10004,2,'D6_SOLD','Day6 固定：已售（用于负向用例）',99.00,'https://img.example.com/d6/sold.jpg','回归测试','sold',0,NULL,0,'2026-01-04 20:35:32','2026-01-04 20:35:32'),(10005,2,'D6_OFF_SHELF_UP','Day6 固定：下架（用于 on-shelf）',77.00,'https://img.example.com/d6/off2.jpg','回归测试','under_review',0,NULL,0,'2026-01-04 20:35:32','2026-01-04 20:51:44'),(10006,2,'D6_OFF_SHELF_DEL','Day6 固定：下架（用于 delete）',55.00,'https://img.example.com/d6/off3.jpg','回归测试','off_shelf',0,NULL,1,'2026-01-04 20:35:32','2026-01-04 20:51:44'),(900001,2,'FX-在售-耳机A','Day6/Day7 回归专用：可售商品池A',199.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-08 22:12:20'),(900002,2,'FX-在售-键盘B','Day6/Day7 回归专用：可售商品池B',299.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-08 22:12:22'),(900003,2,'FX-在售-显示器C','Day6/Day7 回归专用：可售商品池C',899.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-08 22:12:42'),(900004,2,'FX-在售-图书D','Day6/Day7 回归专用：可售商品池D',39.90,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-08 22:12:44'),(900005,2,'FX-在售-台灯E','Day6/Day7 回归专用：可售商品池E',59.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-08 22:29:57'),(900006,2,'FX-在售-背包F','Day6/Day7 回归专用：可售商品池F',129.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-08 22:29:58'),(900101,2,'FX-待审核-商品','用于 under_review 列表/审核通过/驳回测试',77.77,NULL,'回归夹具','under_review',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900201,2,'FX-已下架-商品','用于 off_shelf 列表/重新上架测试',66.66,NULL,'回归夹具','off_shelf',0,'夹具预置下架',0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900301,2,'FX-已售-商品','用于 sold 列表展示/禁止编辑等规则测试',88.88,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900401,2,'FX-订单专用-待支付','订单夹具专用商品',101.00,NULL,'回归夹具','on_sale',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900402,2,'FX-订单专用-已支付','订单夹具专用商品',102.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900403,2,'FX-订单专用-已发货','订单夹具专用商品',103.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900404,2,'FX-订单专用-已完成','订单夹具专用商品',104.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(900405,2,'FX-订单专用-已取消','订单夹具专用商品',105.00,NULL,'回归夹具','sold',0,NULL,0,'2026-01-02 02:02:22','2026-01-02 02:02:22'),(910000,2,'Day12验收商品-请勿删除','用于Day12评价验收的夹具商品',99.90,NULL,'回归夹具','sold',0,NULL,0,'2026-01-28 00:57:41','2026-01-28 00:57:41');
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reviews`
--

DROP TABLE IF EXISTS `reviews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `role` tinyint NOT NULL DEFAULT '1' COMMENT '1=BUYER_TO_SELLER',
  `rating` tinyint NOT NULL,
  `content` varchar(500) NOT NULL,
  `is_anonymous` tinyint NOT NULL DEFAULT '0',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT 'MP逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_order_role` (`order_id`,`role`),
  KEY `idx_product_time` (`product_id`,`create_time`),
  KEY `idx_seller_time` (`seller_id`,`create_time`),
  KEY `idx_buyer_time` (`buyer_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reviews`
--

LOCK TABLES `reviews` WRITE;
/*!40000 ALTER TABLE `reviews` DISABLE KEYS */;
/*!40000 ALTER TABLE `reviews` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_bans`
--

DROP TABLE IF EXISTS `user_bans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_bans` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Ban record ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `ban_type` varchar(10) NOT NULL COMMENT 'TEMP | PERM',
  `reason` varchar(255) DEFAULT NULL COMMENT 'Ban reason',
  `source` varchar(20) NOT NULL COMMENT 'ADMIN | AUTO_RISK | SYSTEM',
  `start_time` datetime NOT NULL COMMENT 'Ban start time',
  `end_time` datetime DEFAULT NULL COMMENT 'Ban end time (NULL for PERM)',
  `created_by` bigint DEFAULT NULL COMMENT 'Operator admin ID (NULL for auto risk)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  PRIMARY KEY (`id`),
  KEY `idx_user_bans_user_id` (`user_id`),
  KEY `idx_user_bans_end_time` (`end_time`),
  CONSTRAINT `fk_user_bans_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户封禁记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_bans`
--

LOCK TABLES `user_bans` WRITE;
/*!40000 ALTER TABLE `user_bans` DISABLE KEYS */;
INSERT INTO `user_bans` VALUES (1,3,'PERM','多次违规且拒不整改，永久封禁','ADMIN','2025-11-28 21:46:44',NULL,1,'2025-11-28 21:46:44');
/*!40000 ALTER TABLE `user_bans` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_credit_logs`
--

DROP TABLE IF EXISTS `user_credit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_credit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `delta` int NOT NULL,
  `reason_type` varchar(64) NOT NULL,
  `reason_note` varchar(255) DEFAULT NULL COMMENT '流水备注',
  `ref_id` bigint DEFAULT NULL,
  `score_before` int NOT NULL,
  `score_after` int NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ucl_user_time` (`user_id`,`create_time`),
  KEY `idx_ucl_reason` (`reason_type`),
  CONSTRAINT `fk_ucl_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_credit_logs`
--

LOCK TABLES `user_credit_logs` WRITE;
/*!40000 ALTER TABLE `user_credit_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_credit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_violations`
--

DROP TABLE IF EXISTS `user_violations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_violations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Violation ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `violation_type` varchar(40) NOT NULL COMMENT 'false_delivery | fake_product etc.',
  `description` text COMMENT 'Violation description',
  `evidence` text COMMENT 'Evidence attachments',
  `punish` varchar(120) DEFAULT NULL COMMENT 'Punishment result',
  `credit` int DEFAULT NULL COMMENT 'Credit change',
  `record_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  PRIMARY KEY (`id`),
  KEY `fk_violation_user` (`user_id`),
  CONSTRAINT `fk_violation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户违规记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_violations`
--

LOCK TABLES `user_violations` WRITE;
/*!40000 ALTER TABLE `user_violations` DISABLE KEYS */;
INSERT INTO `user_violations` VALUES (1,3,'false_delivery','多次出现已标记发货但实际未发货的情况，影响交易安全与体验。','聊天记录截图、物流平台查询结果截图等','封禁账号 30 天，信用分扣 40 分',-40,'2025-11-28 21:46:44','2025-11-28 21:46:44');
/*!40000 ALTER TABLE `user_violations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` varchar(50) NOT NULL COMMENT 'Login username',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt hashed password',
  `mobile` varchar(20) DEFAULT NULL COMMENT 'Mobile phone number',
  `email` varchar(100) DEFAULT NULL COMMENT 'Email address',
  `avatar` varchar(255) DEFAULT NULL COMMENT 'Avatar image URL',
  `credit_score` int NOT NULL DEFAULT '100' COMMENT 'Initial credit score 100',
  `credit_level` varchar(10) NOT NULL DEFAULT 'lv3' COMMENT 'lv1~lv5',
  `credit_updated_at` datetime DEFAULT NULL COMMENT 'credit score updated time',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active | banned',
  `is_seller` int NOT NULL DEFAULT '0' COMMENT '0=buyer,1=seller',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete: 0=normal, 1=deleted',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `nickname` varchar(64) DEFAULT NULL COMMENT 'User nickname',
  `bio` varchar(255) DEFAULT NULL COMMENT 'User bio',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_mobile` (`mobile`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户基本信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'buyer01','$2a$10$7Q3ZExSd8tFRuMmyYL0eIu3vkNDhZGb4IP4SLqw95NTn7FjwF/Hda','13800000001','buyer01@test.com','https://img.example.com/avatar/buyer01.png',100,'lv3',NULL,'active',0,0,'2025-12-03 21:46:44','2025-12-24 19:32:52','测试买家01','用于下单/地址/订单查询的测试账号'),(2,'seller01','$2a$10$VaED6HMu.vXd8QaGh7QqcuI0ZPdIFtj.QvL/lDXEpXL65/9WBm4VO','13800000002','seller01@test.com','https://img.example.com/avatar/seller01.png',100,'lv3',NULL,'active',1,0,'2025-12-03 21:46:44','2026-01-28 00:57:41','测试卖家01','用于发布商品/售卖订单查询的测试账号'),(3,'baduser01','$2a$10$zOEycO1pIvLaSfDLPlQkBuMa9UzUmwZ0sMpNEcBo349fvy4D.LFmG','13800000003','baduser01@test.com','https://img.example.com/avatar/baduser01.png',60,'lv3',NULL,'banned',0,0,'2025-12-03 21:46:44','2025-12-03 21:46:44','违规用户01','用于违规/封禁链路测试的账号（已封禁）'),(4,'admin01','$2a$10$uFZdqmPZNQi1SE9W.2653.Tja9WxLLF/SKMG0IyMmXMYC3eZVCLIu','13900000000','admin01@test.com',NULL,100,'lv3',NULL,'active',0,0,'2025-12-24 19:32:52','2025-12-24 20:01:39','管理员01','Day3 审核回归账号'),(5,'admin','$2a$10$uFZdqmPZNQi1SE9W.2653.Tja9WxLLF/SKMG0IyMmXMYC3eZVCLIu','13900000001','admin@test.com',NULL,100,'lv3',NULL,'active',0,0,'2025-12-24 20:02:19','2025-12-24 20:02:19','管理员','Day3 回归 admin 别名');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-01-31 18:54:35
