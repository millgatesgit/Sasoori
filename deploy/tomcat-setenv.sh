#!/bin/bash
# /opt/tomcat/bin/setenv.sh
# Tomcat startup environment — secrets + JVM tuning
# chmod 600 /opt/tomcat/bin/setenv.sh && chown tomcat:tomcat /opt/tomcat/bin/setenv.sh
#
# DO NOT commit this file to git.

# ── Load secrets from secure env file ────────────────────────────────────────
# shellcheck disable=SC1091
if [ -f /opt/sasoori/sasoori.env ]; then
  set -a
  source /opt/sasoori/sasoori.env
  set +a
fi

# ── JVM options ───────────────────────────────────────────────────────────────
JAVA_OPTS="
  -Xms512m
  -Xmx2g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/var/log/sasoori/heap-dump.hprof
  -Dfile.encoding=UTF-8
  -Djava.net.preferIPv4Stack=true
"

# Export as single-line (Tomcat expects no newlines in JAVA_OPTS)
JAVA_OPTS=$(echo "$JAVA_OPTS" | tr -s ' ' | tr '\n' ' ')
export JAVA_OPTS

# ── Catalina options ──────────────────────────────────────────────────────────
CATALINA_OPTS="-Dlog4j.configurationFile=/opt/tomcat/conf/log4j2.xml"
export CATALINA_OPTS
