
-- NOTES: THIS AREAS NEED TO BE INVESTIGATE IF NEED

-- Migration: Create auth_sessions table for session management
-- Description: Adds persistent session tracking with automatic expiry
-- 
-- This table stores active authentication sessions. Sessions are automatically
-- expired based on the expires_at timestamp. The table includes:
-- - session_id: Unique identifier for each session
-- - user_id: Reference to the authenticated user
-- - created_at: Session creation timestamp
-- - last_accessed: Last activity timestamp (updated with each operation)
-- - expires_at: Session expiry timestamp

CREATE TABLE IF NOT EXISTS auth_sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Create index on user_id for efficient lookup of user's sessions
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id);

-- Create index on expires_at for efficient cleanup of expired sessions
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions(expires_at);

-- Optional: Add a trigger to automatically update last_accessed
-- (Uncomment if not updating manually in application)
-- CREATE OR REPLACE FUNCTION update_auth_sessions_last_accessed()
-- RETURNS TRIGGER AS $$
-- BEGIN
--     NEW.last_accessed = CURRENT_TIMESTAMP;
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;
--
-- CREATE TRIGGER trg_auth_sessions_last_accessed
-- BEFORE UPDATE ON auth_sessions
-- FOR EACH ROW
-- EXECUTE FUNCTION update_auth_sessions_last_accessed();
