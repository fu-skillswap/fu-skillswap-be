#!/usr/bin/env bash
set -euo pipefail

RABBIT_CONTAINER="${RABBIT_CONTAINER:-skillswap-rabbitmq}"
if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi
if ! docker inspect "$RABBIT_CONTAINER" >/dev/null 2>&1; then
  echo "rabbit_container=$RABBIT_CONTAINER status=NOT_FOUND"
  exit 1
fi

echo "rabbit_container=$RABBIT_CONTAINER"
docker exec "$RABBIT_CONTAINER" rabbitmq-diagnostics -q ping
docker exec "$RABBIT_CONTAINER" rabbitmqctl list_queues -p / name messages_ready messages_unacknowledged consumers state \
  --formatter=pretty_table
echo "oldest_message_age_seconds=UNAVAILABLE"
echo "oldest_message_age_note=RabbitMQ does not expose per-message age through rabbitmqctl; publish event timestamp and alert from Prometheus/management metrics."
echo "consumer_state_source=rabbitmqctl list_queues consumers,state"
echo "dlq_policy=durable DLX/DLQ is configured by the application; booking/payment events must not receive TTL."
