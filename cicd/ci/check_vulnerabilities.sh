#!/bin/bash

echo "=========================================================="
echo "   CHECK VULNERABILITIES (GCR / ARTIFACT REGISTRY)        "
echo "=========================================================="

if [[ -z "$LOCATION" || -z "$PROJECT_ID" || -z "$ARTIFACT_REGISTRY_REPO" || -z "$IMAGE_NAME" ]]; then
  echo "ERROR: Missing environment variables."
  echo "Ensure you pass: LOCATION, PROJECT_ID, ARTIFACT_REGISTRY_REPO, IMAGE_NAME"
  exit 1
fi

IMAGE_URI="${LOCATION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REGISTRY_REPO}/${IMAGE_NAME}"
echo "Target Image: $IMAGE_URI"

MAX_RETRIES=30
SLEEP_TIME=10

for ((i=1; i<=MAX_RETRIES; i++)); do

  STATUS=$(gcloud artifacts docker images list "$IMAGE_URI" \
    --sort-by="~createTime" \
    --limit=1 \
    --show-occurrences \
    --format="json" \
    | grep "analysisStatus" \
    | awk -F'"' '{print $4}')

  echo "[Attempt $i/$MAX_RETRIES] Analysis Status: '$STATUS'"

  if [[ "$STATUS" == "FINISHED_SUCCESS" ]]; then
      echo "Analysis complete. Checking for CRITICAL vulnerabilities..."

      CRITICAL_LINE=$(gcloud artifacts docker images list "$IMAGE_URI" \
        --sort-by="~createTime" \
        --limit=1 \
        --show-occurrences \
        --format="json" \
        | grep "\"CRITICAL\":")

      if [[ -z "$CRITICAL_LINE" ]]; then
          CRITICAL_COUNT=0
      else
          CRITICAL_COUNT=$(echo "$CRITICAL_LINE" | awk -F':' '{print $2}' | tr -dc '0-9')
      fi

      echo "CRITICAL vulnerabilities detected: $CRITICAL_COUNT"

      if [[ "$CRITICAL_COUNT" -gt 0 ]]; then
          echo "FAILURE: Found $CRITICAL_COUNT CRITICAL vulnerabilities. Build blocked."
          exit 1
      else
          echo "SUCCESS: No CRITICAL vulnerabilities found. Proceeding."
          exit 0
      fi
  fi

  echo "Analysis in progress... waiting ${SLEEP_TIME}s..."
  sleep $SLEEP_TIME
done

echo "TIMEOUT: Vulnerability analysis did not complete within the time limit."
exit 1