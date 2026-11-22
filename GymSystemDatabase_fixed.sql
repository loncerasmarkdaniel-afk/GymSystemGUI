-- Gym Membership System database (Laragon / MySQL / MariaDB)

CREATE DATABASE IF NOT EXISTS gym_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE gym_db;

-- ------------------------------------------------
-- Table 1: membership_plans
-- Basic / Premium / Student with monthly rate and discount
-- ------------------------------------------------
CREATE TABLE membership_plans (
    plan_id           INT           NOT NULL AUTO_INCREMENT,
    plan_name         VARCHAR(50)   NOT NULL,
    monthly_rate      DECIMAL(10,2) NOT NULL,
    discount_percent  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (plan_id),
    UNIQUE (plan_name)
) ENGINE=InnoDB;

-- ------------------------------------------------
-- Table 2: members
-- Member ID shown in the app is "GYM-" + member_id (starts at 1001)
-- Status (ACTIVE / EXPIRED) is derived from expiry_date, see view below
-- ------------------------------------------------
CREATE TABLE members (
    member_id    INT           NOT NULL AUTO_INCREMENT,
    member_name  VARCHAR(100)  NOT NULL,
    phone        CHAR(11)      NOT NULL,
    plan_id      INT           NOT NULL,
    expiry_date  DATE          NOT NULL,
    total_paid   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (member_id),
    CHECK (phone REGEXP '^09[0-9]{9}$'),
    FOREIGN KEY (plan_id) REFERENCES membership_plans(plan_id)
) ENGINE=InnoDB AUTO_INCREMENT=1001;

-- ------------------------------------------------
-- Table 3: payments
-- One row per receipt (registration or renewal)
-- Receipt number shown in the app is "RCT-" + payment_id (starts at 1001)
-- Member name/plan are copied so receipts survive a cancelled membership
-- ------------------------------------------------
CREATE TABLE payments (
    payment_id       INT           NOT NULL AUTO_INCREMENT,
    member_id        INT           NULL,
    member_name      VARCHAR(100)  NOT NULL,
    plan_name        VARCHAR(50)   NOT NULL,
    payment_type     ENUM('REGISTRATION','RENEWAL') NOT NULL,
    payment_date     DATETIME      NOT NULL,
    months           INT           NOT NULL,
    monthly_rate     DECIMAL(10,2) NOT NULL,
    subtotal         DECIMAL(10,2) NOT NULL,
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_paid       DECIMAL(10,2) NOT NULL,
    cash_amount      DECIMAL(10,2) NOT NULL,
    change_amount    DECIMAL(10,2) NOT NULL,
    valid_until      DATE          NOT NULL,
    PRIMARY KEY (payment_id),
    FOREIGN KEY (member_id) REFERENCES members(member_id)
        ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1001;

-- ------------------------------------------------
-- Starting plans (same rates as the Java code)
-- Premium gets a 10% discount
-- ------------------------------------------------
INSERT INTO membership_plans (plan_name, monthly_rate, discount_percent) VALUES
    ('Basic',     500.00,  0),
    ('Premium',  1500.00, 10),
    ('Student',   400.00,  0);

-- ------------------------------------------------
-- View: what the "Manage Members" table shows
-- ------------------------------------------------
CREATE VIEW member_list AS
SELECT CONCAT('GYM-', m.member_id)    gym_dbmembers                AS member_id,
       m.member_name                                  AS name,
       p.plan_name                                    AS plan,
       IF(m.expiry_date >= CURDATE(), 'ACTIVE', 'EXPIRED') AS status,
       DATE_FORMAT(m.expiry_date, '%b %d, %Y')        AS expires
FROM members m
JOIN membership_plans p ON p.plan_id = m.plan_id;