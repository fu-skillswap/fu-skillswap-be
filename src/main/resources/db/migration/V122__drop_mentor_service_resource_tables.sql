-- rollout: CONTRACT
-- Retire the mentor-service document feature after its API and application code are removed.
DROP TABLE IF EXISTS mentor_service_resource_upload_intents;
DROP TABLE IF EXISTS mentor_service_resources;
