from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path

import pandas as pd


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


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

    raise FileNotFoundError(f"Imagem não encontrada para path '{stored_path}'.")


def copy_or_link(source: Path, target: Path, copy_files: bool) -> None:
    if copy_files:
        shutil.copy2(source, target)
        return

    try:
        os.link(source, target)
    except OSError:
        shutil.copy2(source, target)


def load_existing_annotation_paths(batches_dir: Path, current_batch_name: str) -> set[str]:
    existing_paths: set[str] = set()
    if not batches_dir.exists():
        return existing_paths

    for batch_dir in batches_dir.iterdir():
        if not batch_dir.is_dir() or batch_dir.name == current_batch_name:
            continue
        metadata_path = batch_dir / "metadata.csv"
        if not metadata_path.exists():
            continue
        metadata_df = pd.read_csv(metadata_path)
        if "image_path" not in metadata_df.columns:
            continue
        existing_paths.update(metadata_df["image_path"].astype(str).tolist())

    return existing_paths


def choose_diverse_rows(df: pd.DataFrame, target_size: int, include_all_remote: bool, seed: int) -> pd.DataFrame:
    rng = df.sample(frac=1.0, random_state=seed)
    remote_df = rng[rng["source_kind"] == "remote_db"].copy()
    legacy_df = rng[rng["source_kind"] != "remote_db"].copy()

    selected_frames: list[pd.DataFrame] = []
    selected_count = 0

    if include_all_remote and not remote_df.empty:
        selected_frames.append(remote_df)
        selected_count += len(remote_df)
    elif not remote_df.empty:
        remote_target = min(len(remote_df), max(1, target_size // 5))
        selected_frames.append(remote_df.head(remote_target))
        selected_count += remote_target

    remaining = max(0, target_size - selected_count)
    if remaining == 0:
        return pd.concat(selected_frames, ignore_index=True).drop_duplicates(subset=["image_path"])

    per_species = legacy_df.groupby("scientific_name", dropna=False, sort=False).head(1)
    per_species = per_species.head(remaining)
    selected_frames.append(per_species)
    selected_count += len(per_species)
    remaining = max(0, target_size - selected_count)

    if remaining > 0:
        already_selected_paths = set(pd.concat(selected_frames, ignore_index=True)["image_path"].tolist())
        extra_pool = legacy_df[~legacy_df["image_path"].isin(already_selected_paths)]
        selected_frames.append(extra_pool.head(remaining))

    return pd.concat(selected_frames, ignore_index=True).drop_duplicates(subset=["image_path"]).head(target_size)


def choose_hard_case_rows(df: pd.DataFrame, target_size: int, include_all_remote: bool, seed: int) -> pd.DataFrame:
    rng = df.sample(frac=1.0, random_state=seed)
    remote_df = rng[rng["source_kind"] == "remote_db"].copy()
    legacy_df = rng[rng["source_kind"] != "remote_db"].copy()

    for column in ("quality_score", "blur_score", "blank_ratio", "edge_density", "contrast"):
        if column in legacy_df.columns:
            legacy_df[column] = pd.to_numeric(legacy_df[column], errors="coerce")

    legacy_df["difficulty_score"] = 0.0
    if "quality_score" in legacy_df.columns:
        legacy_df["difficulty_score"] += (1.0 - legacy_df["quality_score"].fillna(0.5)).clip(lower=0.0)
    if "blur_score" in legacy_df.columns:
        blur = legacy_df["blur_score"].fillna(legacy_df["blur_score"].median() if legacy_df["blur_score"].notna().any() else 0.0)
        legacy_df["difficulty_score"] += blur.rank(pct=True, method="average")
    if "blank_ratio" in legacy_df.columns:
        legacy_df["difficulty_score"] += legacy_df["blank_ratio"].fillna(0.0)
    if "edge_density" in legacy_df.columns:
        legacy_df["difficulty_score"] += (1.0 - legacy_df["edge_density"].fillna(legacy_df["edge_density"].median() if legacy_df["edge_density"].notna().any() else 0.0)).clip(lower=0.0)
    if "contrast" in legacy_df.columns:
        legacy_df["difficulty_score"] += (1.0 - legacy_df["contrast"].rank(pct=True, method="average").fillna(0.5))

    legacy_df = legacy_df.sort_values(
        by=["difficulty_score", "scientific_name"],
        ascending=[False, True],
        kind="stable",
    )

    selected_frames: list[pd.DataFrame] = []
    selected_count = 0

    if include_all_remote and not remote_df.empty:
        selected_frames.append(remote_df)
        selected_count += len(remote_df)

    remaining = max(0, target_size - selected_count)
    if remaining == 0:
        return pd.concat(selected_frames, ignore_index=True).drop_duplicates(subset=["image_path"])

    per_species = legacy_df.groupby("scientific_name", dropna=False, sort=False).head(1).head(remaining)
    selected_frames.append(per_species)
    selected_count += len(per_species)
    remaining = max(0, target_size - selected_count)

    if remaining > 0:
        already_selected_paths = set(pd.concat(selected_frames, ignore_index=True)["image_path"].tolist())
        extra_pool = legacy_df[~legacy_df["image_path"].isin(already_selected_paths)]
        selected_frames.append(extra_pool.head(remaining))

    return pd.concat(selected_frames, ignore_index=True).drop_duplicates(subset=["image_path"]).head(target_size)


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepara um lote inicial de anotação YOLO.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]
    batch_cfg = config["annotation_batch"]

    manifests_dir = Path(dataset_cfg["manifests_dir"])
    manifest_path = manifests_dir / dataset_cfg["manifest_name"]
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifesto não encontrado: {manifest_path}")

    raw_dir = Path(dataset_cfg["raw_dir"])
    extra_roots = [Path(path) for path in config.get("legacy_sources", {}).get("root_dirs", [])]
    batches_dir = Path(dataset_cfg["annotation_batches_dir"])
    batch_dir = batches_dir / batch_cfg["name"]
    images_dir = batch_dir / "images"
    labels_dir = batch_dir / "labels"

    ensure_empty_dir(images_dir)
    ensure_empty_dir(labels_dir)

    df = pd.read_csv(manifest_path)
    df = df[df["image_path"].astype(str).str.strip() != ""].copy()
    if bool(batch_cfg.get("exclude_existing_batches", False)):
        existing_paths = load_existing_annotation_paths(batches_dir, current_batch_name=batch_cfg["name"])
        df = df[~df["image_path"].astype(str).isin(existing_paths)].copy()

    selection_strategy = str(batch_cfg.get("selection_strategy", "diverse")).strip().lower()
    if selection_strategy == "hard_cases":
        selected_df = choose_hard_case_rows(
            df=df,
            target_size=int(batch_cfg["target_size"]),
            include_all_remote=bool(batch_cfg.get("include_all_remote", True)),
            seed=int(batch_cfg.get("seed", 42)),
        )
    else:
        selected_df = choose_diverse_rows(
            df=df,
            target_size=int(batch_cfg["target_size"]),
            include_all_remote=bool(batch_cfg.get("include_all_remote", True)),
            seed=int(batch_cfg.get("seed", 42)),
        )

    copy_files = bool(batch_cfg.get("copy_files", True))
    exported_rows: list[dict] = []

    for index, row in enumerate(selected_df.to_dict("records"), start=1):
        source_image = resolve_image_path(raw_dir, str(row["image_path"]), extra_roots)
        if source_image.suffix.lower() not in IMAGE_EXTENSIONS:
            continue

        image_name = (
            f"{index:04d}_"
            f"{str(row.get('source_kind', 'unknown')).strip()}_"
            f"{source_image.name}"
        )
        target_image = images_dir / image_name
        target_label = labels_dir / Path(image_name).with_suffix(".txt")

        copy_or_link(source_image, target_image, copy_files=copy_files)
        target_label.write_text("", encoding="utf-8")

        exported_rows.append(
            {
                **row,
                "resolved_source_path": str(source_image),
                "annotation_image_path": str(target_image.resolve()),
                "annotation_label_path": str(target_label.resolve()),
            }
        )

    exported_df = pd.DataFrame(exported_rows)
    exported_df.to_csv(batch_dir / "metadata.csv", index=False)

    summary = {
        "batch_name": batch_cfg["name"],
        "images": int(len(exported_df)),
        "source_kinds": exported_df["source_kind"].value_counts().to_dict(),
        "species_count": int(exported_df["scientific_name"].nunique()),
        "selection_strategy": selection_strategy,
    }
    (batch_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Lote de anotação criado em: {batch_dir.resolve()}")
    print(f"Imagens: {len(exported_df)}")
    print(f"Espécies: {exported_df['scientific_name'].nunique()}")
    print(f"Origens: {exported_df['source_kind'].value_counts().to_dict()}")


if __name__ == "__main__":
    main()
