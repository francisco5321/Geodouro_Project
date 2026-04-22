#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${1:-config.local.json}"

mapfile -t VALUES < <(python - "$CONFIG_PATH" <<'PY'
import json
import sys
from pathlib import Path

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
print(config["server"]["ssh_host"])
print(config["server"]["remote_images_path"])
print(config["dataset"]["raw_dir"])
PY
)

SSH_HOST="${VALUES[0]}"
REMOTE_IMAGES_PATH="${VALUES[1]}"
RAW_DIR="${VALUES[2]}"

mkdir -p "$RAW_DIR"
rsync -avz --progress "${SSH_HOST}:${REMOTE_IMAGES_PATH%/}/" "$RAW_DIR/"
