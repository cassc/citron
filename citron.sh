#!/bin/bash
cd /opt/apps/citron/


CITRON_FILE_ROOT=/media \
CITRON_MAX_PREVIEW_SIZE=1024 \
CITRON_LOG_FILE=citron.log \
CITRON_LOG_LEVEL=info \
CITRON_IP=192.168.123.5 \
                CITRON_PORT=9090 \
                java -server -Xms256m -Xmx256m -jar citron.jar
