-- Create refresh_tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Create index on token for validation
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

-- Create index on expires_at for cleanup queries
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
