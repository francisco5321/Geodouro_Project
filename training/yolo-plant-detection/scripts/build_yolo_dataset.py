from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path
from typing import Any

import pandas as pd
import yaml
from sklearn.model_selection import train_test_split


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def stratify_labels(df: pd.DataFrame) -> pd.Series | None:
    if "has_boxes" not in df.columns:
        return None

    value_counts = df["has_boxes"].value_counts()
    if value_counts.empty or int(value_counts.min()) < 2:
        return None

    return df["has_boxes"]


def split_dataframe(df: pd.DataFrame, val_ratio: float, test_ratio: float, seed: int) -> dict[str, pd.DataFrame]:
    stratify = stratify_labels(df)
    train_val_df, test_df = train_test_split(
        df,
        test_size=test_ratio,
        random_state=seed,
        shuffle=True,
        stratify=stratify,
    )
    adjusted_val_ratio = val_ratio / (1.0 - test_ratio)
    train_val_stratify = stratify_labels(train_val_df)
    train_df, val_df = train_test_split(
        train_val_df,
        test_size=adjusted_val_ratio,
        random_state=seed,
        shuffle=True,
        stratify=train_val_stratify,
    )
    return {"train": train_df, "val": val_df, "test": test_df}


def ensure_empty_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def normalize_host_path(path_value: str) -> Path:
    normalized = path_value.replace("\\", "/").strip()
    if len(normalized) >= 3 and normalized[1:3] == ":/":
        drive_letter = normalized[0].lower()
        remainder = normalized[3:]
        return Path("/mnt") / drive_letter / remainder
    return Path(normalized)


def resolve_image_path(
    raw_dir: Path,
    stored_path: str,
    extra_roots: list[Path],
) -> Path:
    candidate = normalize_host_path(stored_path)
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


def normalize_boolean(value: Any) -> bool:
    if isinstance(value, bool):
        return value

    normalized = str(value).strip().lower()
    return normalized in {"1", "true", "yes", "y"}


def build_annotation_indexes(annotations_dir: Path) -> tuple[dict[str, Path], dict[str, Path]]:
    exact_index: dict[str, Path] = {}
    suffix_index: dict[str, Path] = {}

    if not annotations_dir.exists():
        return exact_index, suffix_index

    for label_path in annotations_dir.glob("*.txt"):
        exact_index[label_path.name] = label_path
        if "_" in label_path.stem:
            suffix_index[label_path.stem.split("_", 1)[1] + ".txt"] = label_path

    return exact_index, suffix_index


def resolve_annotation_label_path(
    annotations_dir: Path,
    image_path: Path,
    row: dict,
    exact_index: dict[str, Path],
    suffix_index: dict[str, Path],
) -> Path:
    direct = resolve_label_path(annotations_dir, image_path)
    if direct.exists():
        return direct

    basename_txt = image_path.with_suffix(".txt").name
    exact_match = exact_index.get(basename_txt)
    if exact_match is not None:
        return exact_match

    source_kind = str(row.get("source_kind", "")).strip()
    if source_kind:
        suffix_key = f"{source_kind}_{image_path.stem}.txt"
        suffix_match = suffix_index.get(suffix_key)
        if suffix_match is not None:
            return suffix_match

    return direct


def load_reviewed_annotation_rows(
    config: dict,
    annotations_dir: Path,
    annotation_exact_index: dict[str, Path],
    annotation_suffix_index: dict[str, Path],
    extra_roots: list[Path],
) -> pd.DataFrame:
    dataset_cfg = config["dataset"]
    batches_dir = Path(dataset_cfg["annotation_batches_dir"])
    requested_batches = set(dataset_cfg.get("annotation_batch_names", []))
    metadata_paths = sorted(batches_dir.glob("*/metadata.csv"))
    rows: list[dict[str, Any]] = []
    seen_source_paths: set[str] = set()

    for metadata_path in metadata_paths:
        batch_name = metadata_path.parent.name
        if requested_batches and batch_name not in requested_batches:
            continue

        metadata_df = pd.read_csv(metadata_path)
        for row in metadata_df.to_dict("records"):
            resolved_source_raw = str(row.get("resolved_source_path", "")).strip()
            if not resolved_source_raw:
                continue

            source_image = normalize_host_path(resolved_source_raw)
            if not source_image.exists():
                image_path = str(row.get("image_path", "")).strip()
                if not image_path:
                    continue
                source_image = resolve_image_path(Path(dataset_cfg["raw_dir"]), image_path, extra_roots)

            source_key = str(source_image.resolve()).lower()
            if source_key in seen_source_paths:
                continue

            source_label: Path | None = None
            annotation_label_path = str(row.get("annotation_label_path", "")).strip()
            if annotation_label_path:
                candidate = annotations_dir / Path(annotation_label_path).name
                if candidate.exists():
                    source_label = candidate

            if source_label is None:
                source_label = resolve_annotation_label_path(
                    annotations_dir=annotations_dir,
                    image_path=source_image,
                    row=row,
                    exact_index=annotation_exact_index,
                    suffix_index=annotation_suffix_index,
                )

            if not source_label.exists():
                continue

            seen_source_paths.add(source_key)
            rows.append(
                {
                    **row,
                    "batch_name": batch_name,
                    "image_path": str(source_image),
                    "source_label_path": str(source_label),
                    "has_boxes": source_label.stat().st_size > 0,
                    "is_reviewed_negative": source_label.stat().st_size == 0,
                    "is_negative": normalize_boolean(row.get("is_negative", False)),
                }
            )

    return pd.DataFrame(rows)


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
    legacy_cfg = config.get("legacy_sources", {})
    extra_roots = [normalize_host_path(path) for path in legacy_cfg.get("root_dirs", [])]
    annotation_exact_index, annotation_suffix_index = build_annotation_indexes(annotations_dir)
    build_mode = str(dataset_cfg.get("build_mode", "manifest_all")).strip().lower()

    if build_mode == "annotated_only":
        df = load_reviewed_annotation_rows(
            config=config,
            annotations_dir=annotations_dir,
            annotation_exact_index=annotation_exact_index,
            annotation_suffix_index=annotation_suffix_index,
            extra_roots=extra_roots,
        )
        if df.empty:
            raise RuntimeError("Nenhuma anotacao revista encontrada para construir o dataset YOLO.")
    else:
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
            if build_mode == "annotated_only":
                source_image = Path(str(row["image_path"]))
                source_label = Path(str(row["source_label_path"]))
            else:
                source_image = resolve_image_path(
                    raw_dir,
                    str(row["image_path"]),
                    extra_roots,
                )
                source_label = resolve_annotation_label_path(
                    annotations_dir=annotations_dir,
                    image_path=source_image,
                    row=row,
                    exact_index=annotation_exact_index,
                    suffix_index=annotation_suffix_index,
                )

            if source_image.suffix.lower() not in IMAGE_EXTENSIONS:
                continue

            target_image = yolo_dir / "images" / split_name / source_image.name
            target_label = yolo_dir / "labels" / split_name / source_image.with_suffix(".txt").name

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
        positives = int(split_df.get("has_boxes", pd.Series(dtype=bool)).sum()) if "has_boxes" in split_df.columns else None
        negatives = int((~split_df["has_boxes"]).sum()) if "has_boxes" in split_df.columns else None
        if positives is None or negatives is None:
            print(f"{split_name}: {len(split_df)}")
        else:
            print(f"{split_name}: {len(split_df)} (positivas={positives}, negativas={negatives})")


def main() -> None:
    parser = argparse.ArgumentParser(description="Constroi dataset YOLO a partir de manifesto + anotacoes.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    build_dataset(config)


if __name__ == "__main__":
    main()
