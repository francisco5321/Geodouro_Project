from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def collect_imagefolder_rows(raw_dir: Path) -> pd.DataFrame:
    rows = []
    for class_dir in sorted(path for path in raw_dir.iterdir() if path.is_dir()):
        label = class_dir.name
        for image_path in sorted(class_dir.rglob("*")):
            if image_path.is_file() and image_path.suffix.lower() in IMAGE_EXTENSIONS:
                rows.append({"image_path": str(image_path.resolve()), "label": label})

    if not rows:
        raise FileNotFoundError(
            f"No images found in {raw_dir}. Expected data/raw/<label>/*.jpg"
        )

    return pd.DataFrame(rows)


def stratified_or_random_split(
    df: pd.DataFrame,
    test_size: float,
    seed: int,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    labels = df["label"]
    counts = labels.value_counts()
    stratify = labels if counts.min() >= 2 and len(counts) > 1 else None

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
    parser = argparse.ArgumentParser(description="Build train/val/test CSV splits from data/raw.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]
    raw_dir = Path(dataset_cfg["raw_dir"])
    splits_dir = Path(dataset_cfg["splits_dir"])
    val_ratio = float(dataset_cfg["val_ratio"])
    test_ratio = float(dataset_cfg["test_ratio"])
    seed = int(dataset_cfg["seed"])

    random.seed(seed)
    df = collect_imagefolder_rows(raw_dir)

    train_val_df, test_df = stratified_or_random_split(df, test_ratio, seed)
    adjusted_val_ratio = val_ratio / (1.0 - test_ratio)
    train_df, val_df = stratified_or_random_split(train_val_df, adjusted_val_ratio, seed)

    splits_dir.mkdir(parents=True, exist_ok=True)
    train_df.to_csv(splits_dir / "train.csv", index=False)
    val_df.to_csv(splits_dir / "val.csv", index=False)
    test_df.to_csv(splits_dir / "test.csv", index=False)

    labels = sorted(df["label"].unique())
    (splits_dir / "labels.json").write_text(
        json.dumps(labels, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Classes: {len(labels)}")
    print(f"Train: {len(train_df)} | Val: {len(val_df)} | Test: {len(test_df)}")
    print(f"Splits saved to: {splits_dir.resolve()}")


if __name__ == "__main__":
    main()
