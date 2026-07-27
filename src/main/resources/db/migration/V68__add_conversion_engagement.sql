CREATE INDEX IF NOT EXISTS idx_blog_posts_mentor_public_authority
    ON blog_posts (author_user_id, published_at DESC)
    WHERE author_type = 'MENTOR' AND status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS booking_engagement_deliveries (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    recipient_user_id UUID NOT NULL REFERENCES users(id),
    delivery_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_booking_engagement_delivery UNIQUE (booking_id, recipient_user_id, delivery_type)
);

CREATE INDEX IF NOT EXISTS idx_booking_engagement_booking ON booking_engagement_deliveries (booking_id, created_at DESC);
