from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any

import pandas as pd


UNKNOWN_LABELS = {
    "Nao conhecemos essa planta",
    "Não conhecemos essa planta",
    "Não conhecemos essa planta",
}


def load_config(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build hybrid MobileNet splits by augmenting generic iNaturalist train.csv with GeoDouro server images."
    )
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]

    base_splits_dir = Path(str(dataset_cfg["hybrid_base_splits_dir"]).strip())
    raw_dir = Path(dataset_cfg["raw_dir"])
    output_splits_dir = Path(dataset_cfg["splits_dir"])

    if not base_splits_dir.exists():
        raise FileNotFoundError(f"Base splits directory not found: {base_splits_dir}")
    if not raw_dir.exists():
        raise FileNotFoundError(f"Server raw directory not found: {raw_dir}")

    labels = json.loads((base_splits_dir / "labels.json").read_text(encoding="utf-8"))
    known_labels = set(labels)

    train_df = pd.read_csv(base_splits_dir / "train.csv")
    val_df = pd.read_csv(base_splits_dir / "val.csv")
    test_df = pd.read_csv(base_splits_dir / "test.csv")

    server_rows: list[dict[str, str]] = []
    skipped_unknown: dict[str, int] = {}
    skipped_missing_in_base: dict[str, int] = {}

    for class_dir in sorted(path for path in raw_dir.iterdir() if path.is_dir()):
        label = class_dir.name
        image_paths = sorted(path for path in class_dir.rglob("*") if path.is_file())
        if label in UNKNOWN_LABELS:
            skipped_unknown[label] = len(image_paths)
            continue
        if label not in known_labels:
            skipped_missing_in_base[label] = len(image_paths)
            continue
        for image_path in image_paths:
            server_rows.append({"image_path": str(image_path.resolve()), "label": label})

    server_df = pd.DataFrame(server_rows)
    combined_train_df = pd.concat([train_df, server_df], ignore_index=True).drop_duplicates(subset=["image_path"])

    output_splits_dir.mkdir(parents=True, exist_ok=True)
    combined_train_df.to_csv(output_splits_dir / "train.csv", index=False)
    shutil.copy2(base_splits_dir / "val.csv", output_splits_dir / "val.csv")
    shutil.copy2(base_splits_dir / "test.csv", output_splits_dir / "test.csv")
    shutil.copy2(base_splits_dir / "labels.json", output_splits_dir / "labels.json")

    metadata = {
        "source": "hybrid_inat2021_plus_geodouro_server",
        "base_splits_dir": str(base_splits_dir.resolve()),
        "raw_dir": str(raw_dir.resolve()),
        "output_splits_dir": str(output_splits_dir.resolve()),
        "counts": {
            "base_train": len(train_df),
            "base_val": len(val_df),
            "base_test": len(test_df),
            "server_added_to_train": len(server_df),
            "hybrid_train": len(combined_train_df),
            "labels": len(labels),
        },
        "server_class_counts_used": (
            server_df["label"].value_counts().sort_index().to_dict() if not server_df.empty else {}
        ),
        "skipped_unknown_labels": skipped_unknown,
        "skipped_missing_in_base_labels": skipped_missing_in_base,
    }
    (output_splits_dir / "source_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Hybrid splits saved to: {output_splits_dir.resolve()}")
    print(
        f"Counts: train={len(combined_train_df)} | val={len(val_df)} | test={len(test_df)} | classes={len(labels)}"
    )
    print(f"Server images added to train: {len(server_df)}")
    if skipped_unknown:
        print(f"Skipped unknown labels: {skipped_unknown}")
    if skipped_missing_in_base:
        print(f"Skipped labels absent from base model: {skipped_missing_in_base}")


if __name__ == "__main__":
    main()
