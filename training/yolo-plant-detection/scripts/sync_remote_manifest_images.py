from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

import pandas as pd


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def build_scp_base_command(ssh_cfg: dict) -> list[str]:
    command = ["scp", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8"]
    if ssh_cfg.get("port"):
        command.extend(["-P", str(ssh_cfg["port"])])
    if ssh_cfg.get("identity_file"):
        command.extend(["-i", ssh_cfg["identity_file"]])
    return command


def main() -> None:
    parser = argparse.ArgumentParser(description="Sincroniza imagens remotas listadas no manifesto.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]
    ssh_cfg = config["ssh"]

    manifests_dir = Path(dataset_cfg["manifests_dir"])
    manifest_path = manifests_dir / dataset_cfg["manifest_name"]
    raw_dir = Path(dataset_cfg["raw_dir"])
    raw_dir.mkdir(parents=True, exist_ok=True)

    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifesto não encontrado: {manifest_path}")

    df = pd.read_csv(manifest_path)
    remote_df = df[df.get("source_kind", "") == "remote_db"].copy()
    if remote_df.empty:
        print("Não existem imagens remotas no manifesto.")
        return

    relative_paths = sorted(
        {
            str(path).strip().lstrip("/\\")
            for path in remote_df["image_path"].astype(str).tolist()
            if str(path).strip()
        }
    )

    for relative_path in relative_paths:
        source = f"{ssh_cfg['host']}:{ssh_cfg['remote_images_path'].rstrip('/')}/{relative_path}"
        target = raw_dir / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([*build_scp_base_command(ssh_cfg), source, str(target)], check=True)

    print(f"Imagens remotas sincronizadas: {len(relative_paths)}")
    print(f"Destino: {raw_dir.resolve()}")


if __name__ == "__main__":
    main()
