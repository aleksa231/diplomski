-- ==========================================================
-- Remove the JSONB product variant
-- ==========================================================
-- The relational service now exposes a single product model, the
-- normalized (typed EAV) schema from V1. The document-style
-- product_jsonb table and its indexes are no longer used, so they are
-- dropped here instead of editing the already-applied V1 migration.
DROP TABLE IF EXISTS product_jsonb;
