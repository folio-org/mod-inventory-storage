#!/bin/bash
set -e
echo "Creating Kafka topics for mod-inventory-storage..."
# Wait for Kafka to be ready
KAFKA_BROKER="${KAFKA_HOST}:${KAFKA_PORT}"
echo "Waiting for Kafka broker at $KAFKA_BROKER..."
until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$KAFKA_BROKER" > /dev/null 2>&1; do
  echo "Kafka broker not ready yet, waiting..."
  sleep 2
done
echo "Kafka broker is ready!"
# Topics for mod-inventory-storage
TOPICS=(
  "${ENV}.Default.inventory.async-migration"
  "${ENV}.Default.inventory.bound-with"
  "${ENV}.Default.inventory.campus"
  "${ENV}.Default.inventory.classification-type"
  "${ENV}.Default.inventory.call-number-type"
  "${ENV}.Default.inventory.holdings-record"
  "${ENV}.Default.inventory.instance"
  "${ENV}.Default.inventory.instance-contribution"
  "${ENV}.Default.inventory.instance-date-type"
  "${ENV}.Default.inventory.institution"
  "${ENV}.Default.inventory.item"
  "${ENV}.Default.inventory.library"
  "${ENV}.Default.inventory.loan-type"
  "${ENV}.Default.inventory.location"
  "${ENV}.Default.inventory.reindex-records"
  "${ENV}.Default.inventory.reindex.file-ready"
  "${ENV}.Default.inventory.service-point"
  "${ENV}.Default.inventory.subject-source"
  "${ENV}.Default.inventory.subject-type"
  "${ENV}.Default.inventory.material-type"
)
KAFKA_TOPICS_CMD="/opt/kafka/bin/kafka-topics.sh"
for TOPIC in "${TOPICS[@]}"; do
  $KAFKA_TOPICS_CMD \
    --create \
    --bootstrap-server "$KAFKA_BROKER" \
    --replication-factor 1 \
    --partitions "${KAFKA_TOPIC_PARTITIONS}" \
    --topic "$TOPIC" \
    --if-not-exists
  echo "Created topic: $TOPIC"
done
echo "Kafka topics created successfully."
