-- ============================================================
-- V13: subscription_plan_feature
-- Many-to-many join: which features are included in which plans
-- ============================================================

CREATE TABLE subscription_plan_feature (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    plan_id       UUID           NOT NULL,
    feature_id    UUID           NOT NULL,
    is_included   BOOLEAN        NOT NULL DEFAULT TRUE,
    usage_limit   INTEGER,                               -- NULL = unlimited
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_subscription_plan_feature PRIMARY KEY (id),
    CONSTRAINT uq_plan_feature UNIQUE (plan_id, feature_id),
    CONSTRAINT fk_plan_feature_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plan(id),
    CONSTRAINT fk_plan_feature_feature
        FOREIGN KEY (feature_id) REFERENCES subscription_feature(id),
    CONSTRAINT chk_usage_limit CHECK (usage_limit IS NULL OR usage_limit > 0)
);

CREATE INDEX idx_plan_feature_plan    ON subscription_plan_feature(plan_id);
CREATE INDEX idx_plan_feature_feature ON subscription_plan_feature(feature_id);

COMMENT ON TABLE  subscription_plan_feature IS 'Composition table: features included in a specific plan version';
COMMENT ON COLUMN subscription_plan_feature.usage_limit IS 'NULL = unlimited; positive integer = capped usage (e.g. max 500 students)';
