from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path

import pandas as pd
import yaml
from sklearn.model_selection import train_test_split


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def split_dataframe(df: pd.DataFrame, val_ratio: float, test_ratio: float, seed: int) -> dict[str, pd.DataFrame]:
    train_val_df, test_df = train_test_split(df, test_size=test_ratio, random_state=seed, shuffle=True)
    adjusted_val_ratio = val_ratio / (1.0 - test_ratio)
    train_df, val_df = train_test_split(
        train_val_df,
        test_size=adjusted_val_ratio,
        random_state=seed,
        shuffle=True,
    )
    return {"train": train_df, "val": val_df, "test": test_df}


def ensure_empty_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def resolve_image_path(raw_dir: Path, stored_path: str, extra_roots: list[Path]) -> Path:
    candidate = Path(stored_path)
    if candidate.is_absolute() and candidate.exists():
        return candidate

    relative = stored_path.lstrip("/\\")
    candidate = raw_dir / relative
    if candidate.exists():
        return candidate

    for root_dir in extra_roots:
        candidate = root_dir / relative
        if candidate.exists():
            return candidate

    raise FileNotFoundError(f"Imagem nao encontrada para path '{stored_path}'. Procurado em '{candidate}'.")


def resolve_label_path(annotations_dir: Path, image_path: Path) -> Path:
    return annotations_dir / image_path.with_suffix(".txt").name


def copy_or_link(source: Path, target: Path, copy_files: bool) -> None:
    if copy_files:
        shutil.copy2(source, target)
        return

    try:
        os.link(source, target)
    except OSError:
        shutil.copy2(source, target)


def build_dataset(config: dict) -> None:
    dataset_cfg = config["dataset"]
    raw_dir = Path(dataset_cfg["raw_dir"])
    manifests_dir = Path(dataset_cfg["manifests_dir"])
    annotations_dir = Path(dataset_cfg["annotations_dir"])
    yolo_dir = Path(dataset_cfg["yolo_dir"])
    manifest_path = manifests_dir / dataset_cfg["manifest_name"]
    extra_roots = [Path(path) for path in config.get("legacy_sources", {}).get("root_dirs", [])]

    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifesto nao encontrado: {manifest_path}")

    df = pd.read_csv(manifest_path)
    if df.empty:
        raise RuntimeError("Manifesto vazio.")

    required_columns = {"image_path"}
    missing = required_columns - set(df.columns)
    if missing:
        raise RuntimeError(f"Manifesto invalido. Faltam colunas: {sorted(missing)}")

    splits = split_dataframe(
        df=df,
        val_ratio=float(dataset_cfg["val_ratio"]),
        test_ratio=float(dataset_cfg["test_ratio"]),
        seed=int(dataset_cfg["seed"]),
    )

    for split_name in ("train", "val", "test"):
        ensure_empty_dir(yolo_dir / "images" / split_name)
        ensure_empty_dir(yolo_dir / "labels" / split_name)

    copy_files = bool(dataset_cfg.get("copy_files", True))
    total = 0

    for split_name, split_df in splits.items():
        for row in split_df.to_dict("records"):
            source_image = resolve_image_path(raw_dir, str(row["image_path"]), extra_roots)
            if source_image.suffix.lower() not in IMAGE_EXTENSIONS:
                continue

            target_image = yolo_dir / "images" / split_name / source_image.name
            target_label = yolo_dir / "labels" / split_name / source_image.with_suffix(".txt").name
            source_label = resolve_label_path(annotations_dir, source_image)

            copy_or_link(source_image, target_image, copy_files=copy_files)

            if source_label.exists():
                copy_or_link(source_label, target_label, copy_files=copy_files)
            else:
                target_label.write_text("", encoding="utf-8")

            total += 1

    data_yaml = {
        "path": str(yolo_dir.resolve()),
        "train": "images/train",
        "val": "images/val",
        "test": "images/test",
        "names": {0: "plant"},
        "nc": 1,
    }

    (yolo_dir / "data.yaml").write_text(
        yaml.safe_dump(data_yaml, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )

    print(f"Dataset YOLO criado em: {yolo_dir.resolve()}")
    print(f"Total de imagens processadas: {total}")
    for split_name, split_df in splits.items():
        print(f"{split_name}: {len(split_df)}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Constroi dataset YOLO a partir de manifesto + anotacoes.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    build_dataset(config)


if __name__ == "__main__":
    main()
