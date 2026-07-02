
-- Migration File
-- IMPORTANT: Check or Backup before running this migration file.
-- The latest DB Schema MD has been updated if you haven't construct DB RUN db schema instead of this file.

ALTER TABLE users
ADD COLUMN auth_secretKey VARCHAR(100);

ALTER TABLE users
ADD COLUMN auth_enabled BOOLEAN DEFAULT FALSE NOT NULL;