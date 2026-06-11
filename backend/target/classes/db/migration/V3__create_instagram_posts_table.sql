-- Create instagram_posts table
CREATE TABLE instagram_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL,
    post_url VARCHAR(500) NOT NULL UNIQUE,
    post_shortcode VARCHAR(50),
    post_type VARCHAR(50) NOT NULL CHECK (post_type IN ('POST', 'REEL', 'VIDEO', 'CAROUSEL')),
    caption TEXT,
    hashtags TEXT[],
    mentions TEXT[],
    media_url VARCHAR(500),
    posted_at TIMESTAMP WITH TIME ZONE,
    is_competitor_post BOOLEAN NOT NULL DEFAULT false,
    monitoring_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (monitoring_status IN ('ACTIVE', 'PAUSED', 'ERROR')),
    last_fetched_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile FOREIGN KEY (profile_id) REFERENCES instagram_profiles(id) ON DELETE CASCADE
);

-- Create unique index on post_url for faster lookups and prevent duplicates
CREATE UNIQUE INDEX idx_instagram_posts_post_url ON instagram_posts(post_url);

-- Create index on profile_id for join optimization
CREATE INDEX idx_instagram_posts_profile_id ON instagram_posts(profile_id);

-- Create index on monitoring_status for filtering active posts
CREATE INDEX idx_instagram_posts_monitoring_status ON instagram_posts(monitoring_status);

-- Create index on last_fetched_at for monitoring scheduler queries
CREATE INDEX idx_instagram_posts_last_fetched_at ON instagram_posts(last_fetched_at);
