#!/bin/bash
set -euo pipefail

APP_USER="${APP_USER:-sudoer}"
APP_GROUP="${APP_GROUP:-$APP_USER}"
APP_HOME="${APP_HOME:-/home/$APP_USER}"
APP_DIR="${APP_DIR:-$APP_HOME/Geodouro_Project}"
UPLOAD_DIR="${UPLOAD_DIR:-/opt/geodouro/uploads}"
ENV_DIR="/etc/geodouro"
ENV_FILE="$ENV_DIR/backend.env"
SYSTEMD_DIR="/etc/systemd/system"
COMPOSE_TEMPLATE="$APP_DIR/tools/deploy/geodouro-compose.service"
BACKEND_TEMPLATE="$APP_DIR/tools/deploy/geodouro-backend.service"
COMPOSE_SERVICE="$SYSTEMD_DIR/geodouro-compose.service"
BACKEND_SERVICE="$SYSTEMD_DIR/geodouro-backend.service"
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-geodouro-postgres}"
DB_NAME="${DB_NAME:-geodouro}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-GeoFlora_BD123}"
SERVER_PORT="${SERVER_PORT:-8080}"
APP_AUTH_TOKEN_SECRET="${APP_AUTH_TOKEN_SECRET:-geodouro-auth-key}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script with sudo."
  exit 1
fi

if [[ ! -d "$APP_DIR" ]]; then
  echo "Project directory not found: $APP_DIR"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but was not found."
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required but was not found."
  exit 1
fi

COMPOSE_CMD=""
if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD="/usr/bin/docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD="$(command -v docker-compose)"
fi

HAS_EXISTING_DOCKER_POSTGRES=0
if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q 'geodouro-postgres'; then
  HAS_EXISTING_DOCKER_POSTGRES=1
fi

DB_SERVICE_UNIT="postgresql.service"
USE_COMPOSE_POSTGRES=0
POSTGRES_PORT="5432"

if [[ -n "$COMPOSE_CMD" && "$HAS_EXISTING_DOCKER_POSTGRES" -eq 1 ]]; then
  DB_SERVICE_UNIT="geodouro-compose.service"
  USE_COMPOSE_POSTGRES=1
  POSTGRES_PORT="5432"
elif command -v pg_lsclusters >/dev/null 2>&1; then
  CLUSTER_LINE="$(pg_lsclusters --no-header 2>/dev/null | awk '$4 == "online" { print; exit }')"
  if [[ -n "$CLUSTER_LINE" ]]; then
    CLUSTER_VERSION="$(awk '{print $1}' <<<"$CLUSTER_LINE")"
    CLUSTER_NAME="$(awk '{print $2}' <<<"$CLUSTER_LINE")"
    POSTGRES_PORT="$(awk '{print $3}' <<<"$CLUSTER_LINE")"
    DB_SERVICE_UNIT="postgresql@${CLUSTER_VERSION}-${CLUSTER_NAME}.service"
  elif systemctl is-active --quiet postgresql || systemctl is-enabled --quiet postgresql; then
    DB_SERVICE_UNIT="postgresql.service"
  elif [[ -n "$COMPOSE_CMD" ]]; then
    DB_SERVICE_UNIT="geodouro-compose.service"
    USE_COMPOSE_POSTGRES=1
  else
    echo "Neither a native postgresql cluster/service nor a working Docker Compose command is available."
    exit 1
  fi
elif systemctl is-active --quiet postgresql || systemctl is-enabled --quiet postgresql; then
  DB_SERVICE_UNIT="postgresql.service"
elif [[ -n "$COMPOSE_CMD" ]]; then
  DB_SERVICE_UNIT="geodouro-compose.service"
  USE_COMPOSE_POSTGRES=1
else
  echo "Neither a native postgresql service nor a working Docker Compose command is available."
  exit 1
fi

mkdir -p "$UPLOAD_DIR" "$ENV_DIR"
chown -R "$APP_USER:$APP_GROUP" "$UPLOAD_DIR"

if [[ "$USE_COMPOSE_POSTGRES" -eq 1 ]]; then
  install -m 0644 "$COMPOSE_TEMPLATE" "$COMPOSE_SERVICE"
  sed -i "s|__START_CMD__|/usr/bin/docker start $POSTGRES_CONTAINER_NAME|g" "$COMPOSE_SERVICE"
  sed -i "s|__STOP_CMD__|/usr/bin/docker stop $POSTGRES_CONTAINER_NAME|g" "$COMPOSE_SERVICE"
else
  rm -f "$COMPOSE_SERVICE"
fi

install -m 0644 "$BACKEND_TEMPLATE" "$BACKEND_SERVICE"
sed -i "s|User=sudoer|User=$APP_USER|g" "$BACKEND_SERVICE"
sed -i "s|/home/sudoer|$APP_HOME|g" "$BACKEND_SERVICE"
sed -i "s|__DB_SERVICE_UNIT__|$DB_SERVICE_UNIT|g" "$BACKEND_SERVICE"

cat > "$ENV_FILE" <<EOF
DB_URL=jdbc:postgresql://localhost:$POSTGRES_PORT/$DB_NAME
DB_USERNAME=$DB_USERNAME
DB_PASSWORD=$DB_PASSWORD
SERVER_PORT=$SERVER_PORT
APP_AUTH_TOKEN_SECRET=$APP_AUTH_TOKEN_SECRET
OBSERVATION_IMAGES_DIR=$UPLOAD_DIR
EOF

chmod 600 "$ENV_FILE"

if [[ ! -f "$APP_DIR/backend/build/libs/geodouro-backend-0.0.1-SNAPSHOT.jar" ]]; then
  su - "$APP_USER" -c "cd '$APP_DIR' && ./gradlew :backend:bootJar"
fi

if [[ "$USE_COMPOSE_POSTGRES" -eq 1 ]]; then
  CURRENT_DOCKER_NAME="$(docker ps -a --format '{{.Names}}' 2>/dev/null | grep 'geodouro-postgres' | head -n 1 || true)"
  if [[ -n "$CURRENT_DOCKER_NAME" && "$CURRENT_DOCKER_NAME" != "$POSTGRES_CONTAINER_NAME" ]]; then
    if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx "$POSTGRES_CONTAINER_NAME"; then
      docker rm -f "$POSTGRES_CONTAINER_NAME"
    fi
    docker rename "$CURRENT_DOCKER_NAME" "$POSTGRES_CONTAINER_NAME"
  fi
  docker update --restart unless-stopped "$POSTGRES_CONTAINER_NAME" >/dev/null
elif [[ "$USE_COMPOSE_POSTGRES" -eq 0 ]]; then
  if [[ "$DB_USERNAME" == "postgres" ]]; then
    sudo -u postgres psql -p "$POSTGRES_PORT" -c "ALTER USER postgres WITH PASSWORD '$DB_PASSWORD';"
  else
    sudo -u postgres psql -p "$POSTGRES_PORT" -tc "SELECT 1 FROM pg_roles WHERE rolname = '$DB_USERNAME'" | grep -q 1 \
      || sudo -u postgres psql -p "$POSTGRES_PORT" -c "CREATE ROLE $DB_USERNAME LOGIN PASSWORD '$DB_PASSWORD';"
    sudo -u postgres psql -p "$POSTGRES_PORT" -c "ALTER USER $DB_USERNAME WITH PASSWORD '$DB_PASSWORD';"
  fi

  sudo -u postgres psql -p "$POSTGRES_PORT" -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 \
    || sudo -u postgres createdb -p "$POSTGRES_PORT" -O "$DB_USERNAME" "$DB_NAME"
fi

systemctl daemon-reload
if [[ "$USE_COMPOSE_POSTGRES" -eq 1 ]]; then
  systemctl enable docker
  systemctl enable geodouro-compose.service
  systemctl restart geodouro-compose.service
else
  systemctl enable "$DB_SERVICE_UNIT"
fi
systemctl enable geodouro-backend.service
systemctl restart geodouro-backend.service

echo
if [[ "$USE_COMPOSE_POSTGRES" -eq 1 ]]; then
  systemctl --no-pager --full status geodouro-compose.service geodouro-backend.service
  echo
  docker ps --filter name=geodouro-postgres
else
  systemctl --no-pager --full status postgresql.service geodouro-backend.service
fi
