CREATE DATABASE IF NOT EXISTS campus_cycle_db;
USE campus_cycle_db;

CREATE TABLE IF NOT EXISTS cycles (
    cycle_id VARCHAR(50) PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    owner_phone VARCHAR(20) NOT NULL,

    -- 1. Use MySQL native ENUM for strict type safety
    cycle_type ENUM('CITY_COMMUTER', 'CARGO_UTILITY', 'ELECTRIC_SCOOTER') NOT NULL,
    physical_condition ENUM('Perfect', 'Usuable', 'Broken') DEFAULT 'Usuable',

    -- 2. Proper Date/Time types:
    -- purchase_date: DATETIME or DATE (when the owner bought the bike)
    purchase_date DATETIME NULL,

    -- registered_at: TIMESTAMP with automatic current timestamp default
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 3. Booleans with sensible defaults
    is_gear BOOLEAN NOT NULL DEFAULT FALSE,
    needs_fuel BOOLEAN NOT NULL DEFAULT FALSE,
    needs_liscence BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,

    -- 4. Index for fast search by phone or registration date
    INDEX idx_owner_phone (owner_phone),
    INDEX idx_registered_at (registered_at)
);