-- =====================================
-- JEZDIBOLT – V1 INIT MIGRATION (FINAL)
-- =====================================

-- ==========================
-- USERS
-- ==========================
CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       contact VARCHAR(50),
                       role VARCHAR(20) NOT NULL DEFAULT 'driver',
                       password_hash VARCHAR(60) NOT NULL
);

-- ==========================
-- CARS
-- ==========================
CREATE TABLE cars (
                      id SERIAL PRIMARY KEY,
                      license_plate VARCHAR(20) NOT NULL UNIQUE,
                      brand VARCHAR(50) NOT NULL,
                      model VARCHAR(50) NOT NULL,
                      year INTEGER NOT NULL,
                      fuel_type VARCHAR(20) NOT NULL,
                      stk_valid_until DATE,
                      color VARCHAR(30),
                      city VARCHAR(50) NOT NULL,
                      notes TEXT,
                      photo_url VARCHAR(255)
);

-- ==========================
-- IMPORT BATCHES
-- ==========================
CREATE TABLE import_batches (
                                id SERIAL PRIMARY KEY,
                                filename VARCHAR(255) NOT NULL,
                                iso_week VARCHAR(10) NOT NULL,
                                company VARCHAR(255) NOT NULL,
                                city VARCHAR(100),
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================
-- BOLT EARNINGS
-- ==========================
CREATE TABLE bolt_earnings (
                               id SERIAL PRIMARY KEY,
                               user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
                               batch_id INTEGER REFERENCES import_batches(id) ON DELETE CASCADE,
                               driver_identifier VARCHAR(100),
                               unique_identifier VARCHAR(100),
                               gross_total_kc DECIMAL(12,2),
                               tips_kc DECIMAL(12,2),
                               hourly_gross_kc DECIMAL(12,2),
                               hours_worked DECIMAL(10,2) NOT NULL,
                               cash_taken_kc DECIMAL(12,2),
                               applied_rate INTEGER,
                               earnings_kc DECIMAL(12,2),
                               settlement_kc DECIMAL(12,2),
                               partially_paid_kc DECIMAL(12,2) DEFAULT 0,
                               paid BOOLEAN DEFAULT FALSE,
                               paid_at TIMESTAMP,
                               bonus_kc DECIMAL(12,2) DEFAULT 0,
                               penalty_kc DECIMAL(12,2) DEFAULT 0,
                               UNIQUE (unique_identifier, batch_id)
);

-- ==========================
-- PAY RATES
-- ==========================
CREATE TABLE pay_rates (
                           id SERIAL PRIMARY KEY,
                           min_gross INTEGER NOT NULL,
                           max_gross INTEGER,
                           rate_per_hour INTEGER NOT NULL
);

-- ==========================
-- PAY RULES
-- ==========================
CREATE TABLE pay_rules (
                           id SERIAL PRIMARY KEY,
                           type VARCHAR(50) NOT NULL,
                           hours INTEGER NOT NULL,
                           adjustment INTEGER NOT NULL,
                           mode VARCHAR(10) NOT NULL
);

-- ==========================
-- RENTAL RECORDS
-- ==========================
CREATE TABLE rental_records (
                                id SERIAL PRIMARY KEY,
                                car_id INTEGER REFERENCES cars(id),
                                user_id INTEGER REFERENCES users(id),
                                start_date DATE NOT NULL,
                                end_date DATE,
                                price_per_week DECIMAL(10,2),
                                total_price DECIMAL(10,2),
                                notes TEXT,
                                contract_type VARCHAR(20) NOT NULL
);

-- ==========================
-- CAR ASSIGNMENTS
-- ==========================
CREATE TABLE car_assignments (
                                 id SERIAL PRIMARY KEY,
                                 car_id INTEGER REFERENCES cars(id),
                                 user_id INTEGER REFERENCES users(id),
                                 shift_type VARCHAR(20) NOT NULL,
                                 start_date DATE NOT NULL,
                                 end_date DATE,
                                 notes TEXT
);

-- ==========================
-- PENALTIES
-- ==========================
CREATE TABLE penalties (
                           id SERIAL PRIMARY KEY,
                           car_id INTEGER REFERENCES cars(id) ON DELETE CASCADE,
                           user_id INTEGER REFERENCES users(id),
                           date DATE NOT NULL,
                           amount DECIMAL(10,2) NOT NULL,
                           description TEXT,
                           paid BOOLEAN DEFAULT FALSE,
                           paid_at TIMESTAMP,
                           resolved_by INTEGER REFERENCES users(id)
);

-- ==========================
-- HISTORY LOGS
-- ==========================
CREATE TABLE history_logs (
                              id SERIAL PRIMARY KEY,
                              timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              admin_id INTEGER REFERENCES users(id),
                              action VARCHAR(100) NOT NULL,
                              entity VARCHAR(100) NOT NULL,
                              entity_id INTEGER,
                              details TEXT NOT NULL
);
