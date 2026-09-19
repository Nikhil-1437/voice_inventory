-- ============================================================
-- Voice-Based Inventory Management — MySQL Schema
-- Import this file in MySQL Workbench (File > Run SQL Script)
-- or:  mysql -u root -p < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS voice_inventory_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE voice_inventory_db;

-- ------------------------------------------------------------
-- products: the shop's catalog. Supports a regional-language
-- name so voice recognition in Hindi/Marathi/Tamil etc. can
-- match products by their spoken name too.
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stock_transactions;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    local_name          VARCHAR(150) DEFAULT NULL COMMENT 'Name in the owner''s regional language, e.g. Hindi/Tamil',
    category            VARCHAR(100) DEFAULT 'General',
    unit                VARCHAR(20)  NOT NULL DEFAULT 'piece' COMMENT 'piece, kg, g, litre, dozen, bag, carton, box, quintal',
    quantity            DECIMAL(12,2) NOT NULL DEFAULT 0,
    price_per_unit      DECIMAL(12,2) NOT NULL DEFAULT 0,
    low_stock_threshold DECIMAL(12,2) NOT NULL DEFAULT 5,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_product_name (name)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- stock_transactions: every voice or manual stock movement.
-- type = IN  (stock received)  |  OUT (stock sold/removed)
-- source = VOICE | MANUAL
-- ------------------------------------------------------------
CREATE TABLE stock_transactions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT NOT NULL,
    type            ENUM('IN','OUT') NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL,
    unit            VARCHAR(20) NOT NULL,
    price_per_unit  DECIMAL(12,2) DEFAULT NULL,
    source          ENUM('VOICE','MANUAL') NOT NULL DEFAULT 'MANUAL',
    raw_transcript  TEXT DEFAULT NULL COMMENT 'Original spoken text, for voice transactions',
    language        VARCHAR(10) DEFAULT NULL COMMENT 'BCP-47 code of the speech, e.g. hi-IN, en-IN',
    note            VARCHAR(255) DEFAULT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_product_created (product_id, created_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Sample data so the dashboard is populated on first run
-- ------------------------------------------------------------
INSERT INTO products (name, local_name, category, unit, quantity, price_per_unit, low_stock_threshold) VALUES
('Rice',        'चावल',   'Grains',    'kg',     120, 55.00, 20),
('Wheat Flour', 'आटा',    'Grains',    'kg',     8,   40.00, 15),
('Sugar',       'चीनी',   'Grocery',   'kg',     45,  48.00, 10),
('Toor Dal',    'तूर दाल', 'Pulses',    'kg',     18,  130.00, 10),
('Cooking Oil', 'तेल',    'Grocery',   'litre',  6,   150.00, 8),
('Salt',        'नमक',    'Grocery',   'bag',    30,  20.00, 5),
('Tea Powder',  'चाय पत्ती','Beverages', 'box',    3,   85.00, 5),
('Soap',        'साबुन',  'Personal Care','dozen', 4,  180.00, 3),
('Biscuits',    'बिस्कुट', 'Snacks',    'carton', 2,  450.00, 3),
('Onions',      'प्याज',   'Vegetables','quintal',1.5, 2200.00, 1);

INSERT INTO stock_transactions (product_id, type, quantity, unit, price_per_unit, source, note) VALUES
(1, 'IN', 120, 'kg', 55.00, 'MANUAL', 'Opening stock'),
(2, 'IN', 20, 'kg', 40.00, 'MANUAL', 'Opening stock'),
(2, 'OUT', 12, 'kg', 40.00, 'MANUAL', 'Sold to customers'),
(3, 'IN', 50, 'kg', 48.00, 'MANUAL', 'Opening stock'),
(3, 'OUT', 5, 'kg', 48.00, 'MANUAL', 'Sold to customers');
