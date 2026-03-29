#!/bin/bash
# Sasoori — Zero-downtime deploy script
# Usage: ./deploy/deploy.sh [VPS_USER@VPS_HOST]
# Example: ./deploy/deploy.sh ubuntu@123.45.67.89
set -euo pipefail

VPS="${1:-ubuntu@sasoori.com}"
TOMCAT_HOME="/opt/tomcat"
FRONTEND_DIR="/var/www/sasoori/frontend"
ARTIFACT="target/sasoori-backend-1.0.0-SNAPSHOT.war"

echo "=== Building Sasoori Backend ==="
mvn clean package -DskipTests -q
echo "Build complete: ${ARTIFACT}"

echo "=== Deploying Backend to ${VPS} ==="
scp "${ARTIFACT}" "${VPS}:${TOMCAT_HOME}/webapps/ROOT.war"

echo "=== Deploying Frontend to ${VPS} ==="
rsync -az --delete --exclude='.DS_Store' frontend/ "${VPS}:${FRONTEND_DIR}/"

echo "=== Reloading Nginx ==="
ssh "${VPS}" "sudo systemctl reload nginx"

echo "=== Waiting for Tomcat to redeploy WAR (30s) ==="
sleep 30

echo "=== Smoke test ==="
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "https://sasoori.com/api/v1/config")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "✓ /api/v1/config returned 200 — deploy successful!"
else
  echo "✗ /api/v1/config returned ${HTTP_CODE} — check Tomcat logs!"
  ssh "${VPS}" "sudo tail -50 ${TOMCAT_HOME}/logs/catalina.out"
  exit 1
fi
