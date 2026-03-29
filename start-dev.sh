#!/usr/bin/env bash
# Dev server startup script for Sasoori backend
# Run from E:\Work\Sasoori directory

export MAVEN_HOME="/c/Program Files/NetBeans-23/netbeans/java/maven"
export PATH="$MAVEN_HOME/bin:$PATH"
export JAVA_HOME="/c/Program Files/Java/jdk-17"

# PipeFix agent is required on this machine to work around a Windows JDK 17 bug
# where AF_UNIX loopback sockets fail with EINVAL, causing NIO Selector to fail.
# The agent patches PipeImpl.noUnixDomainSockets=true so TCP loopback is used instead.
export MAVEN_OPTS="-javaagent:E:/Work/Sasoori/pipefix.jar"

# Application environment
export DB_URL="jdbc:postgresql://localhost:5432/sasoori_db"
export DB_USER="sasoori"
export DB_PASSWORD="sasoori123"
export GOOGLE_CLIENT_ID="dev"
export GOOGLE_CLIENT_SECRET="dev"
export FRONTEND_URL="http://localhost:3000,http://localhost:5500"
export SECURE_COOKIE="false"
export DEV_MODE="true"

cd "E:/Work/Sasoori"
mvn jetty:run
