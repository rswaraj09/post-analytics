-- Create post_metrics table
CREATE TABLE post_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    likes_count BIGINT,
    comments_count BIGINT,
    views_count BIGINT,
    video_views BIGINT,
    reach BIGINT,
    impressions BIGINT,
    saves BIGINT,
    shares BIGINT,
    profile_visits BIGINT,
    engagement_rate NUMERIC(6, 4),
    is_estimated BOOLEAN NOT NULL DEFAULT false,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_post FOREIGN KEY (post_id) REFERENCES instagram_posts(id) ON DELETE CASCADE
);

-- Create composite index on post_id and fetched_at for historical queries
CREATE INDEX idx_post_metrics_post_id_fetched_at ON post_metrics(post_id, fetched_at DESC);

-- Create index on post_id for join optimization
CREATE INDEX idx_post_metrics_post_id ON post_metrics(post_id);

-- Create index on fetched_at for time-based queries
CREATE INDEX idx_post_metrics_fetched_at ON post_metrics(fetched_at);
