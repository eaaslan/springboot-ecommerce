-- Runs on first volume initialization. Creates per-service databases.
CREATE USER product WITH PASSWORD 'pass';
CREATE DATABASE productdb OWNER product;
GRANT ALL PRIVILEGES ON DATABASE productdb TO product;

CREATE USER inventory WITH PASSWORD 'pass';
CREATE DATABASE inventorydb OWNER inventory;
GRANT ALL PRIVILEGES ON DATABASE inventorydb TO inventory;

CREATE USER payment WITH PASSWORD 'pass';
CREATE DATABASE paymentdb OWNER payment;
GRANT ALL PRIVILEGES ON DATABASE paymentdb TO payment;

CREATE USER orderusr WITH PASSWORD 'pass';
CREATE DATABASE orderdb OWNER orderusr;
GRANT ALL PRIVILEGES ON DATABASE orderdb TO orderusr;

CREATE USER notification WITH PASSWORD 'pass';
CREATE DATABASE notificationdb OWNER notification;
GRANT ALL PRIVILEGES ON DATABASE notificationdb TO notification;
