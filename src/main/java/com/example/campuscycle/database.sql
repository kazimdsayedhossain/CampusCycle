CREATE DATABASE IF NOT EXISTS campus_cycle_db;
USE campus_cycle_db;

CREATE TABLE IF NOT EXISTS cycles (
    cycle_id VARCHAR(50) PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    owner_phone VARCHAR(20) NOT NULL,
    cycle_type VARCHAR(50) NOT NULL,
    physical_condition VARCHAR(50) NOT NULL,
    purchase_date TIMESTAMP NULL,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_gear BOOLEAN DEFAULT FALSE,
    needs_fuel BOOLEAN DEFAULT FALSE,
    needs_liscence BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    is_available BOOLEAN DEFAULT TRUE
);


INSERT INTO cycles (
    cycle_id, owner_name, owner_phone, cycle_type,
    physical_condition, purchase_date, registered_at,
    is_gear, needs_fuel, needs_liscence, is_verified, is_available
) VALUES
('CC-TEST001', 'Sayed', '01712345678', 'CITY_COMMUTER', 'Perfect', NOW(), NOW(), FALSE, FALSE, FALSE, TRUE, TRUE),
('CC-CARGO02', 'Rahim', '01898765432', 'CARGO_UTILITY', 'Usuable', NOW(), NOW(), TRUE, FALSE, FALSE, TRUE, TRUE),
('CC-SCOOT03', 'Karim', '01911223344', 'ELECTRIC_SCOOTER', 'Perfect', NOW(), NOW(), FALSE, TRUE, TRUE, TRUE, TRUE);