#!/bin/bash
set -eu

cd "$HOME/Geodouro_Project"
pkill -f 'geodouro-backend-0.0.1-SNAPSHOT.jar' || true
rm -f backend/backend.log
nohup env \
  DB_URL='jdbc:postgresql://localhost:5432/geodouro' \
  DB_USERNAME='postgres' \
  DB_PASSWORD='postgres' \
  SERVER_PORT='8080' \
  APP_AUTH_TOKEN_SECRET='geodouro-auth-key' \
  OBSERVATION_IMAGES_DIR='/opt/geodouro/uploads' \
  java -jar backend/build/libs/geodouro-backend-0.0.1-SNAPSHOT.jar \
  > backend/backend.log 2>&1 < /dev/null &
