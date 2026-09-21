#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
topic="${INPUT_TOPIC:-status-events}"

publish() {
  local file_name="$1"
  docker compose -f "$project_dir/docker-compose.yml" exec -T kafka \
    kafka-console-producer --bootstrap-server kafka:29092 --topic "$topic" \
    < "$project_dir/scripts/$file_name"
  echo "Published $file_name"
}

publish flight.json
publish flight.json
publish partner.json
publish manual-adjustment.json
publish reversal.json
publish reversal-first.json
publish original-after-reversal.json
publish invalid-message.json

echo
echo "Sample events published. Swagger UI: http://localhost:8080/swagger-ui.html"
