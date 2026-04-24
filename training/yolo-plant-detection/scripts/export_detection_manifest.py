from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

import pandas as pd
import psycopg


MANIFEST_SQL = """
WITH observation_images_resolved AS (
    SELECT
        o.observation_id,
        COALESCE(o.device_observation_id::text, '') AS device_observation_id,
        COALESCE(o.enriched_scientific_name, o.predicted_scientific_name, ps.scientific_name) AS scientific_name,
        oi.image_path AS image_path,
        'observation_image' AS source_table
    FROM observation o
    LEFT JOIN plant_species ps ON ps.plant_species_id = o.plant_species_id
    JOIN observation_image oi ON oi.observation_id = o.observation_id
    WHERE oi.image_path IS NOT NULL AND trim(oi.image_path) <> ''

    UNION ALL

    SELECT
        o.observation_id,
        COALESCE(o.device_observation_id::text, '') AS device_observation_id,
        COALESCE(o.enriched_scientific_name, o.predicted_scientific_name, ps.scientific_name) AS scientific_name,
        o.image_uri AS image_path,
        'observation.image_uri' AS source_table
    FROM observation o
    LEFT JOIN plant_species ps ON ps.plant_species_id = o.plant_species_id
    WHERE o.image_uri IS NOT NULL
      AND trim(o.image_uri) <> ''
      AND NOT EXISTS (
          SELECT 1 FROM observation_image oi WHERE oi.observation_id = o.observation_id
      )
)
SELECT DISTINCT
    observation_id,
    device_observation_id,
    COALESCE(scientific_name, 'Unknown') AS scientific_name,
    image_path,
    source_table
FROM observation_images_resolved
ORDER BY observation_id DESC, image_path ASC
"""


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def build_ssh_base_command(ssh_cfg: dict) -> list[str]:
    command = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8"]
    if ssh_cfg.get("port"):
        command.extend(["-p", str(ssh_cfg["port"])])
    if ssh_cfg.get("identity_file"):
        command.extend(["-i", ssh_cfg["identity_file"]])
    command.append(ssh_cfg["host"])
    return command


def export_remote_db_manifest_via_psycopg(config: dict) -> pd.DataFrame:
    db_cfg = config["database"]
    conninfo = (
        f"host={db_cfg['host']} "
        f"port={db_cfg['port']} "
        f"dbname={db_cfg['name']} "
        f"user={db_cfg['user']} "
        f"password={db_cfg['password']}"
    )

    with psycopg.connect(conninfo) as connection:
        rows = pd.read_sql(MANIFEST_SQL, connection)

    return rows


def export_remote_db_manifest_via_ssh(config: dict) -> pd.DataFrame:
    ssh_cfg = config["ssh"]
    sql = " ".join(line.strip() for line in MANIFEST_SQL.strip().splitlines())
    copy_sql = f"COPY ({sql}) TO STDOUT WITH CSV HEADER"
    remote_command = (
        f"PGPASSWORD='{ssh_cfg['remote_db_password']}' "
        f"psql -h localhost -U {ssh_cfg['remote_db_user']} -d {ssh_cfg['remote_db_name']} "
        f"-c \"{copy_sql}\""
    )
    result = subprocess.run(
        [*build_ssh_base_command(ssh_cfg), remote_command],
        capture_output=True,
        text=True,
        check=True,
    )
    from io import StringIO
    return pd.read_csv(StringIO(result.stdout))


def normalize_remote_rows(rows: pd.DataFrame) -> pd.DataFrame:
    if rows.empty:
        return rows

    rows = rows.copy()
    rows["image_path"] = rows["image_path"].astype(str).str.strip()
    rows = rows[rows["image_path"] != ""].copy()
    rows["label_name"] = "plant"
    rows["is_negative"] = False
    rows["source_kind"] = "remote_db"
    rows["quality_score"] = pd.NA
    rows["blur_score"] = pd.NA
    rows["contrast"] = pd.NA
    rows["entropy"] = pd.NA
    rows["blank_ratio"] = pd.NA
    rows["edge_density"] = pd.NA
    rows["width"] = pd.NA
    rows["height"] = pd.NA
    rows["legacy_split"] = pd.NA
    return rows


def load_species_map(path: Path | None) -> dict[str, str]:
    if path is None or not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def collect_legacy_rows(config: dict) -> pd.DataFrame:
    legacy_cfg = config.get("legacy_sources", {})
    if not legacy_cfg.get("enabled", False):
        return pd.DataFrame()

    split_csvs = [Path(path) for path in legacy_cfg.get("split_csvs", [])]
    if split_csvs:
        csv_frames: list[pd.DataFrame] = []
        for csv_path in split_csvs:
            if not csv_path.exists():
                continue
            frame = pd.read_csv(csv_path)
            if "local_image_path" not in frame.columns:
                continue
            split_name = csv_path.stem
            csv_frames.append(
                pd.DataFrame(
                    {
                        "observation_id": None,
                        "device_observation_id": "",
                        "scientific_name": frame.get("scientific_name", pd.Series(["Unknown"] * len(frame)))
                            .astype(str)
                            .str.replace("_", " ", regex=False),
                        "image_path": frame["local_image_path"].astype(str),
                        "source_table": split_name,
                        "label_name": "plant",
                        "is_negative": False,
                        "source_kind": "legacy_split_csv",
                        "quality_score": frame.get("quality_score", pd.Series([pd.NA] * len(frame))),
                        "blur_score": frame.get("blur_score", pd.Series([pd.NA] * len(frame))),
                        "contrast": frame.get("contrast", pd.Series([pd.NA] * len(frame))),
                        "entropy": frame.get("entropy", pd.Series([pd.NA] * len(frame))),
                        "blank_ratio": frame.get("blank_ratio", pd.Series([pd.NA] * len(frame))),
                        "edge_density": frame.get("edge_density", pd.Series([pd.NA] * len(frame))),
                        "width": frame.get("width", pd.Series([pd.NA] * len(frame))),
                        "height": frame.get("height", pd.Series([pd.NA] * len(frame))),
                        "legacy_split": frame.get("split", pd.Series([split_name] * len(frame))),
                    }
                )
            )
        if csv_frames:
            return pd.concat(csv_frames, ignore_index=True).drop_duplicates(subset=["image_path"])

    split_dirs = [Path(path) for path in legacy_cfg.get("split_dirs", [])]
    species_map = load_species_map(
        Path(legacy_cfg["species_map_path"]) if legacy_cfg.get("species_map_path") else None
    )

    rows: list[dict] = []
    for split_dir in split_dirs:
        if not split_dir.exists():
            continue

        split_name = split_dir.name
        for class_dir in sorted(path for path in split_dir.iterdir() if path.is_dir()):
            class_id = class_dir.name
            scientific_name = species_map.get(class_id, class_id).replace("_", " ")
            for image_path in class_dir.rglob("*"):
                if not image_path.is_file():
                    continue
                rows.append(
                    {
                        "observation_id": None,
                        "device_observation_id": "",
                        "scientific_name": scientific_name,
                        "image_path": str(image_path.resolve()),
                        "source_table": split_name,
                        "label_name": "plant",
                        "is_negative": False,
                        "source_kind": "legacy_imagefolder",
                    }
                )

    return pd.DataFrame(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description="Exporta manifesto de imagens para deteção YOLO.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    db_cfg = config["database"]
    dataset_cfg = config["dataset"]

    manifests_dir = Path(dataset_cfg["manifests_dir"])
    manifests_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = manifests_dir / dataset_cfg["manifest_name"]

    db_mode = str(db_cfg.get("mode", "direct")).lower()
    try:
        if db_mode == "ssh":
            remote_rows = export_remote_db_manifest_via_ssh(config)
        else:
            remote_rows = export_remote_db_manifest_via_psycopg(config)
    except Exception as exc:
        print(f"Aviso: falha ao exportar imagens remotas ({exc}).")
        remote_rows = pd.DataFrame()

    remote_rows = normalize_remote_rows(remote_rows)
    legacy_rows = collect_legacy_rows(config)

    remote_manifest_path = manifests_dir / dataset_cfg.get("remote_manifest_name", "detection_manifest_remote.csv")
    merged_manifest_path = manifests_dir / dataset_cfg.get("merged_manifest_name", "detection_manifest_merged.csv")

    if not remote_rows.empty:
        remote_rows.to_csv(remote_manifest_path, index=False)

    merged_rows = pd.concat([legacy_rows, remote_rows], ignore_index=True)
    if merged_rows.empty:
        raise RuntimeError("Nao foram encontradas imagens nem nas fontes locais nem na BD remota.")

    merged_rows = merged_rows.drop_duplicates(subset=["image_path"])
    merged_rows.to_csv(manifest_path, index=False)
    merged_rows.to_csv(merged_manifest_path, index=False)

    print(f"Manifesto principal: {manifest_path.resolve()}")
    print(f"Manifesto combinado: {merged_manifest_path.resolve()}")
    if not remote_rows.empty:
        print(f"Manifesto remoto: {remote_manifest_path.resolve()}")
    print(f"Imagens legacy: {len(legacy_rows)}")
    print(f"Imagens remotas: {len(remote_rows)}")
    print(f"Imagens totais: {len(merged_rows)}")


if __name__ == "__main__":
    main()
