-- Create audit_logs table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    action_type VARCHAR(50) NOT NULL CHECK (action_type IN ('CREATE', 'UPDATE', 'DELETE')),
    entity_type VARCHAR(50) NOT NULL CHECK (entity_type IN ('PROFILE', 'POST', 'NOTIFICATION_RULE', 'USER')),
    entity_id UUID NOT NULL,
    old_value JSONB,
    new_value JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_audit FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Create index on user_id for filtering audit logs by user
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);

-- Create index on created_at for ordering audit logs
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- Create index on entity_type and entity_id for filtering by entity
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- Create index on action_type for filtering by action
CREATE INDEX idx_audit_logs_action_type ON audit_logs(action_type);
