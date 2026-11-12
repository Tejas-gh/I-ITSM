-- A5: login credentials, kept apart from the users table (CONTRACT_FREEZE.md §N.2: credentials are a separate security class).
-- Only the BCrypt hash is stored, never the password.
CREATE TABLE user_credentials (
    user_id       VARCHAR(100) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
);
