-- roles
INSERT IGNORE INTO roles (id, name) VALUES
(1, 'ADMIN'),
(2, 'PILOT'),
(3, 'CUSTOMER');

-- users (password: password123)
INSERT IGNORE INTO users
(id, name, email, phone_number, password, email_verified, provider, provider_id, active, created_at, updated_at)
VALUES
(1, 'Admin User', 'admin@test.com', '07111111111',
'$2a$10$K63xxELqDQisbnNxEfLXgeFur0vxqkrBoaWIQWFtyvXVaM00ZFysu',
true, 'LOCAL', NULL, true, NOW(), NOW()),

(2, 'Pilot User', 'pilot@test.com', '07222222222',
'$2a$10$O531Aew0glJ8d5IQ7lQF9u0zFoay6x3I3VWlu0SsiRuz/orJJ/v0C',
true, 'LOCAL', NULL, true, NOW(), NOW()),

(3, 'Customer User', 'customer@test.com', '07333333333',
'$2a$10$K63xxELqDQisbnNxEfLXgeFur0vxqkrBoaWIQWFtyvXVaM00ZFysu',
true, 'LOCAL', NULL, true, NOW(), NOW());

-- users_roles
INSERT IGNORE INTO users_roles (user_id, role_id) VALUES
(1, 1),
(1, 2),
(1, 3),
(2, 2),
(2, 3),
(3, 3);

-- airports
INSERT IGNORE INTO airports (id, name, city, country, iata_code) VALUES
(1, 'London Airport', 'LONDON', 'UK', 'LHR'),
(2, 'Beijing Airport', 'BEIJING', 'CHINA', 'PEK'),
(3, 'Shanghai Airport', 'SHANGHAI', 'CHINA', 'SHA'),
(4, 'Miami Airport', 'MIAMI', 'USA', 'MIA');

-- flights
INSERT IGNORE INTO flights
(id, flight_number, status, departure_airport_id, arrival_airport_id,
 departure_time, arrival_time, base_price, assigned_pilot_id, total_seats, available_seats, version)
VALUES
(1, 'MU1001', 'SCHEDULED', 1, 2, '2026-04-10 09:00:00', '2026-04-10 20:00:00', 650.00, 2, 100, 100, 0),
(2, 'MU1002', 'SCHEDULED', 2, 1, '2026-04-15 10:00:00', '2026-04-15 18:00:00', 620.00, 2, 120, 120, 0),
(3, 'MU2001', 'SCHEDULED', 1, 4, '2026-04-20 08:00:00', '2026-04-20 16:00:00', 500.00, 2, 80, 80, 0),
(4, 'MU2002', 'SCHEDULED', 1, 3, '2026-04-22 07:00:00', '2026-04-22 13:00:00', 600.00, 2, 80, 80, 0),
(5, 'MU2003', 'SCHEDULED', 2, 3, '2026-04-23 08:00:00', '2026-04-23 17:00:00', 560.00, 2, 100, 100, 0),
(6, 'MU2004', 'SCHEDULED', 3, 4, '2026-04-25 11:00:00', '2026-04-25 22:00:00', 800.00, 2, 80, 80, 0);

-- bookings
INSERT IGNORE INTO bookings
(id, booking_date, booking_reference, status, flight_id, user_id)
VALUES
(1, '2026-03-10 09:15:00', 'F8F53CFE', 'CONFIRMED', 2, 1),
(2, '2026-03-11 20:33:00', '24C90460', 'CONFIRMED', 4, 1);

-- passengers
INSERT IGNORE INTO passengers
(id, first_name, last_name, passport_number, seat_number, special_request, type, booking_id)
VALUES
(1, 'Gergoe', 'Goldsmith', '21342349018NG', '23A', NULL, 'ADULT', 1),
(2, 'Mary', 'Rint', '23248720384NG', '1A', NULL, 'ADULT', 1),
(3, 'July', 'Ruht', '23287973424NG', '5B', 'Only Beef', 'ADULT', 2),
(4, 'Auly', 'Ullirhs', '98223073445NG', '9C', 'Vegetarian', 'ADULT', 2);
