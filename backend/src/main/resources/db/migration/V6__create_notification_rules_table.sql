-- Create notification_rules table
CREATE TABLE notification_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    metric_type VARCHAR(50) NOT NULL CHECK (metric_type IN ('LIKES', 'COMMENTS', 'VIEWS', 'REACH', 'IMPRESSIONS', 'ENGAGEMENT_RATE')),
    threshold_percentage NUMERIC(6, 2) NOT NULL CHECK (threshold_percentage >= 0 AND threshold_percentage <= 1000),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_rule FOREIGN KEY (post_id) REFERENCES instagram_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_rule FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index on post_id for filtering rules by post
CREATE INDEX idx_notification_rules_post_id ON notification_rules(post_id);

-- Create index on user_id for filtering rules by user
CREATE INDEX idx_notification_rules_user_id ON notification_rules(user_id);

-- Create index on is_active for filtering active rules
CREATE INDEX idx_notification_rules_is_active ON notification_rules(is_active);
