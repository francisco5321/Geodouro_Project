from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import pandas as pd
from sklearn.model_selection import train_test_split
from torchvision.datasets import INaturalist


TAXONOMY_FIELDS = ("kingdom", "phylum", "class", "order", "family", "genus", "species")


def load_config(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def normalize_species_label(genus: str, species: str) -> str:
    genus_text = genus.replace("_", " ").strip()
    species_text = species.replace("_", " ").strip()
    return f"{genus_text} {species_text}".strip()


def parse_full_category(category_name: str) -> dict[str, str]:
    pieces = category_name.split("_")
    if len(pieces) != 8:
        raise ValueError(f"Unexpected iNaturalist category format: {category_name}")

    _, kingdom, phylum, class_name, order, family, genus, species = pieces
    return {
        "kingdom": kingdom,
        "phylum": phylum,
        "class": class_name,
        "order": order,
        "family": family,
        "genus": genus,
        "species": species,
        "label": normalize_species_label(genus, species),
    }


def load_allowed_labels(path: Path | None) -> set[str] | None:
    if path is None:
        return None

    if not path.exists():
        raise FileNotFoundError(f"Allowed labels file not found: {path}")

    allowed = {
        " ".join(line.strip().split())
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }
    return allowed or None


def build_rows(dataset: INaturalist) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for category_id, filename in dataset.index:
        full_category = dataset.category_name("full", category_id)
        taxonomy = parse_full_category(full_category)
        image_path = Path(dataset.root) / full_category / filename
        rows.append(
            {
                "image_path": str(image_path.resolve()),
                "label": taxonomy["label"],
                "kingdom": taxonomy["kingdom"],
                "phylum": taxonomy["phylum"],
                "class": taxonomy["class"],
                "order": taxonomy["order"],
                "family": taxonomy["family"],
                "genus": taxonomy["genus"],
                "species": taxonomy["species"],
            }
        )
    return rows


def apply_filters(
    df: pd.DataFrame,
    allowed_labels: set[str] | None,
    filters: dict[str, str],
    min_images_per_class: int,
) -> pd.DataFrame:
    filtered = df.copy()

    for field, expected in filters.items():
        if expected:
            filtered = filtered[filtered[field] == expected]

    if allowed_labels:
        filtered = filtered[filtered["label"].isin(allowed_labels)]

    if min_images_per_class > 1 and not filtered.empty:
        counts = filtered["label"].value_counts()
        valid_labels = counts[counts >= min_images_per_class].index
        filtered = filtered[filtered["label"].isin(valid_labels)]

    if filtered.empty:
        raise ValueError("No samples left after applying iNaturalist filters.")

    return filtered.reset_index(drop=True)


def stratified_split(
    df: pd.DataFrame,
    test_size: float,
    seed: int,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    labels = df["label"]
    counts = labels.value_counts()
    stratify = labels if len(counts) > 1 and counts.min() >= 2 else None

    try:
        return train_test_split(
            df,
            test_size=test_size,
            random_state=seed,
            shuffle=True,
            stratify=stratify,
        )
    except ValueError:
        return train_test_split(
            df,
            test_size=test_size,
            random_state=seed,
            shuffle=True,
            stratify=None,
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare iNaturalist 2021 CSV splits for the MobileNetV3 training pipeline."
    )
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    parser.add_argument(
        "--download",
        action="store_true",
        help="Download the requested iNaturalist splits with torchvision if they are missing.",
    )
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]

    inat_root = Path(dataset_cfg.get("inat_root", "data/external/inat2021"))
    train_version = str(dataset_cfg.get("inat_train_version", "2021_train_mini"))
    val_version = str(dataset_cfg.get("inat_val_version", "2021_valid"))
    splits_dir = Path(dataset_cfg["splits_dir"])
    seed = int(dataset_cfg.get("seed", 42))
    test_from_val_ratio = float(dataset_cfg.get("inat_test_from_val_ratio", 0.5))
    min_images_per_class = int(dataset_cfg.get("inat_min_images_per_class", 1))
    allowed_labels_path_text = str(dataset_cfg.get("inat_allowed_labels_path", "")).strip()
    allowed_labels_path = Path(allowed_labels_path_text) if allowed_labels_path_text else None
    allowed_labels = load_allowed_labels(allowed_labels_path)

    filters = {
        field: str(dataset_cfg.get(f"inat_{field}_filter", "")).strip()
        for field in TAXONOMY_FIELDS[:-1]
    }

    train_dataset = INaturalist(
        root=inat_root,
        version=train_version,
        download=args.download,
    )
    val_dataset = INaturalist(
        root=inat_root,
        version=val_version,
        download=args.download,
    )

    train_df = pd.DataFrame(build_rows(train_dataset))
    val_df = pd.DataFrame(build_rows(val_dataset))

    train_df = apply_filters(
        train_df,
        allowed_labels=allowed_labels,
        filters=filters,
        min_images_per_class=min_images_per_class,
    )
    val_df = apply_filters(
        val_df,
        allowed_labels=allowed_labels,
        filters=filters,
        min_images_per_class=1,
    )
    train_labels = set(train_df["label"].unique())
    val_df = val_df[val_df["label"].isin(train_labels)].reset_index(drop=True)
    if val_df.empty:
        raise ValueError("Validation split is empty after aligning labels with the filtered training split.")

    if not 0.0 < test_from_val_ratio < 1.0:
        raise ValueError("inat_test_from_val_ratio must be between 0 and 1.")

    val_final_df, test_df = stratified_split(val_df, test_size=test_from_val_ratio, seed=seed)

    splits_dir.mkdir(parents=True, exist_ok=True)
    train_df[["image_path", "label"]].to_csv(splits_dir / "train.csv", index=False)
    val_final_df[["image_path", "label"]].to_csv(splits_dir / "val.csv", index=False)
    test_df[["image_path", "label"]].to_csv(splits_dir / "test.csv", index=False)

    labels = sorted(train_df["label"].unique())
    (splits_dir / "labels.json").write_text(
        json.dumps(labels, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    metadata = {
        "source": "iNaturalist 2021",
        "train_version": train_version,
        "val_version": val_version,
        "inat_root": str(inat_root.resolve()),
        "filters": filters,
        "allowed_labels_path": str(allowed_labels_path.resolve()) if allowed_labels_path else "",
        "min_images_per_class": min_images_per_class,
        "test_from_val_ratio": test_from_val_ratio,
        "counts": {
            "train": len(train_df),
            "val": len(val_final_df),
            "test": len(test_df),
            "labels": len(labels),
        },
    }
    (splits_dir / "source_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Prepared iNaturalist splits in: {splits_dir.resolve()}")
    print(
        "Counts: "
        f"train={len(train_df)} | val={len(val_final_df)} | test={len(test_df)} | classes={len(labels)}"
    )
    if any(filters.values()):
        active_filters = ", ".join(f"{field}={value}" for field, value in filters.items() if value)
        print(f"Applied taxonomy filters: {active_filters}")
    if allowed_labels_path:
        print(f"Applied allowed-labels file: {allowed_labels_path.resolve()}")


if __name__ == "__main__":
    main()
