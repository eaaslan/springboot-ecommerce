-- Runs on first volume initialization. Creates per-service databases.
CREATE USER product WITH PASSWORD 'pass';
CREATE DATABASE productdb OWNER product;
GRANT ALL PRIVILEGES ON DATABASE productdb TO product;
