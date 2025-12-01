#!/bin/bash

set -e

echo "=========================================================="
echo "   SONAR CLOUD ANALYSIS                                   "
echo "=========================================================="

# 1. Check for required environment variables
if [[ -z "$SONAR_CLOUD_TOKEN" ]]; then
  echo "ERROR: SONAR_CLOUD_TOKEN is missing."
  echo "Make sure the secret is correctly mounted in Cloud Build."
  exit 1
fi

if [[ -z "$PROJECT_ID" ]]; then
  echo "ERROR: PROJECT_ID is missing."
  exit 1
fi

echo "Starting Sonar analysis for project: $PROJECT_ID"
echo "Organization: green-energy-tracker-cloud"

mvn sonar:sonar \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.organization=green-energy-tracker-cloud \
  -Dsonar.projectKey="$PROJECT_ID" \
  -Dsonar.login="$SONAR_CLOUD_TOKEN"

echo "Sonar Cloud analysis completed successfully."