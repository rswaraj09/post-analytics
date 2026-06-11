-- Create instagram_profiles table
CREATE TABLE instagram_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    profile_url VARCHAR(500) NOT NULL,
    category VARCHAR(100),
    followers_count BIGINT,
    following_count BIGINT,
    total_posts INTEGER,
    profile_picture_url VARCHAR(500),
    is_business_account BOOLEAN NOT NULL DEFAULT false,
    graph_api_token TEXT,
    added_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_added_by FOREIGN KEY (added_by) REFERENCES users(id) ON DELETE CASCADE
);

-- Create unique index on username for faster lookups and prevent duplicates
CREATE UNIQUE INDEX idx_instagram_profiles_username ON instagram_profiles(username);

-- Create index on added_by for join optimization
CREATE INDEX idx_instagram_profiles_added_by ON instagram_profiles(added_by);
