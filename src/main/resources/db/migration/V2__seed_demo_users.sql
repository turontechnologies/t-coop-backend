-- Demo accounts for local/dev testing — mirrors the frontend's mock users
-- (t-coop-app/src/lib/mock-users.ts) so login works identically end-to-end
-- against the real backend. Password for all three is "admin123", stored
-- as a bcrypt hash (never plaintext). DO NOT rely on this migration running
-- against a real production database — it's dev/demo seed data.

INSERT INTO cooperatives (id, name, admin_name, contact_email, contact_phone, address, country, state, city, status, currency, subscription_fee, withdrawal_fee_percent)
VALUES ('COOP-0001', 'Turon Co-operatives', 'Chidinma Eze', 'chidinma.eze@t-coop.com', '08134567890', '22 Aba Road, GRA Phase 2, Rivers State', 'Nigeria', 'Rivers State', 'Port Harcourt', 'Active', 'NGN', 300000, 1);

-- bcrypt hash of "admin123"
INSERT INTO members (id, cooperative_id, role, password_hash, first_name, last_name, email, status)
VALUES
    ('SA-0001', NULL,         'super_admin', '$2a$10$5caCfNyqZrxDHaiikgWARuTrZVcJQzXUz7c/WZZvr0NuVZl8fC5IK', 'Falola',   'Mayowa', 'mayor@gmail.com',           'Active'),
    ('AD-0001', 'COOP-0001',  'admin',       '$2a$10$5caCfNyqZrxDHaiikgWARuTrZVcJQzXUz7c/WZZvr0NuVZl8fC5IK', 'Chidinma', 'Eze',    'chidinma.eze@t-coop.com',   'Active'),
    ('MB-0001', 'COOP-0001',  'member',      '$2a$10$5caCfNyqZrxDHaiikgWARuTrZVcJQzXUz7c/WZZvr0NuVZl8fC5IK', 'Tunde',    'Bakare', 'adedarasapok@gmail.com',    'Active');
