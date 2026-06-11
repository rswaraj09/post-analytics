-- Create notifications table
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    post_id UUID,
    rule_id UUID,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_notification FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_notification FOREIGN KEY (post_id) REFERENCES instagram_posts(id) ON DELETE SET NULL,
    CONSTRAINT fk_rule_notification FOREIGN KEY (rule_id) REFERENCES notification_rules(id) ON DELETE SET NULL
);

-- Create index on user_id for filtering notifications by user
CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- Create index on created_at for ordering notifications
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

-- Create index on is_read for filtering unread notifications
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
