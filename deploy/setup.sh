#!/bin/bash
# Sasoori VPS Setup Script
# Ubuntu 22.04 LTS — 2 vCPU, 8GB RAM, 100GB NVMe
# Run as root: bash setup.sh
set -euo pipefail

DOMAIN="sasoori.com"
APP_USER="sasoori"
TOMCAT_USER="tomcat"
TOMCAT_VERSION="10.1.20"
JAVA_VERSION="17"
PG_VERSION="15"
FRONTEND_DIR="/var/www/sasoori/frontend"
SECRETS_DIR="/opt/sasoori"
LOG_DIR="/var/log/sasoori"
TOMCAT_HOME="/opt/tomcat"

echo "=== Sasoori VPS Setup ==="

# ── System update ────────────────────────────────────────────────────────────
apt-get update && apt-get upgrade -y
apt-get install -y curl wget gnupg2 unzip git ufw fail2ban

# ── Java 17 ──────────────────────────────────────────────────────────────────
apt-get install -y openjdk-${JAVA_VERSION}-jdk-headless
java -version

# ── PostgreSQL 15 ────────────────────────────────────────────────────────────
apt-get install -y postgresql-${PG_VERSION}
systemctl enable postgresql
systemctl start postgresql

# Create DB and user
sudo -u postgres psql <<SQL
CREATE USER ${APP_USER} WITH PASSWORD 'changeme_in_prod';
CREATE DATABASE sasoori_db OWNER ${APP_USER};
GRANT ALL PRIVILEGES ON DATABASE sasoori_db TO ${APP_USER};
SQL

# Apply performance tuning
cat deploy/postgresql.conf.patch | sudo tee -a /etc/postgresql/${PG_VERSION}/main/postgresql.conf
systemctl restart postgresql

# ── Tomcat 10 ────────────────────────────────────────────────────────────────
useradd -r -m -U -d ${TOMCAT_HOME} -s /bin/false ${TOMCAT_USER} || true
wget -q "https://archive.apache.org/dist/tomcat/tomcat-10/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" -O /tmp/tomcat.tar.gz
tar xzf /tmp/tomcat.tar.gz -C /opt
ln -sfn /opt/apache-tomcat-${TOMCAT_VERSION} ${TOMCAT_HOME}
chown -R ${TOMCAT_USER}:${TOMCAT_USER} ${TOMCAT_HOME}
chmod +x ${TOMCAT_HOME}/bin/*.sh

# Tomcat systemd service
cat > /etc/systemd/system/tomcat.service <<EOF
[Unit]
Description=Apache Tomcat 10
After=network.target postgresql.service

[Service]
Type=forking
User=${TOMCAT_USER}
Group=${TOMCAT_USER}
Environment="JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}-openjdk-amd64"
Environment="CATALINA_HOME=${TOMCAT_HOME}"
Environment="CATALINA_BASE=${TOMCAT_HOME}"
ExecStart=${TOMCAT_HOME}/bin/startup.sh
ExecStop=${TOMCAT_HOME}/bin/shutdown.sh
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable tomcat

# ── Nginx ─────────────────────────────────────────────────────────────────────
apt-get install -y nginx
systemctl enable nginx

# Rate-limit zone — add to nginx.conf http block
sed -i '/http {/a\\tlimit_req_zone $binary_remote_addr zone=auth:10m rate=5r/m;' /etc/nginx/nginx.conf

# ── Let's Encrypt ─────────────────────────────────────────────────────────────
apt-get install -y certbot python3-certbot-nginx
certbot --nginx -d ${DOMAIN} -d www.${DOMAIN} --non-interactive --agree-tos -m admin@${DOMAIN}

# ── Site config ───────────────────────────────────────────────────────────────
cp deploy/nginx.conf /etc/nginx/sites-available/sasoori
ln -sf /etc/nginx/sites-available/sasoori /etc/nginx/sites-enabled/sasoori
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

# ── Directories ──────────────────────────────────────────────────────────────
mkdir -p ${FRONTEND_DIR} ${SECRETS_DIR} ${LOG_DIR}
chown ${TOMCAT_USER}:${TOMCAT_USER} ${LOG_DIR}
chmod 700 ${SECRETS_DIR}

# ── Firewall ──────────────────────────────────────────────────────────────────
ufw allow 22/tcp    comment 'SSH'
ufw allow 80/tcp    comment 'HTTP'
ufw allow 443/tcp   comment 'HTTPS'
ufw deny 8080/tcp   comment 'Block direct Tomcat access'
ufw --force enable

echo ""
echo "=== Setup complete ==="
echo "Next steps:"
echo "  1. Copy deploy/tomcat-setenv.sh to ${TOMCAT_HOME}/bin/setenv.sh"
echo "  2. Create ${SECRETS_DIR}/sasoori.env with your production secrets"
echo "  3. Run: psql -U ${APP_USER} -d sasoori_db -f src/main/resources/db/schema.sql"
echo "  4. Run: psql -U ${APP_USER} -d sasoori_db -f src/main/resources/db/seed.sql"
echo "  5. Deploy WAR: mvn clean package && cp target/*.war ${TOMCAT_HOME}/webapps/ROOT.war"
echo "  6. Deploy frontend: rsync -av frontend/ ${FRONTEND_DIR}/"
echo "  7. Start services: systemctl start tomcat && systemctl reload nginx"
