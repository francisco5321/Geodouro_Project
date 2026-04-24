from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import yaml


SPLITS = ("train", "valid", "test")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Converte um dataset YOLO externo para uma unica classe."
    )
    parser.add_argument("--source", required=True, help="Diretorio do dataset YOLO de origem.")
    parser.add_argument("--output", required=True, help="Diretorio de destino.")
    parser.add_argument(
        "--class-name",
        default="plant",
        help="Nome da classe unica no dataset convertido.",
    )
    return parser.parse_args()


def rewrite_label_file(source_label: Path, target_label: Path) -> None:
    target_label.parent.mkdir(parents=True, exist_ok=True)

    if not source_label.exists():
        target_label.write_text("", encoding="utf-8")
        return

    lines_out: list[str] = []
    for raw_line in source_label.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 5:
            continue
        parts[0] = "0"
        lines_out.append(" ".join(parts))

    target_label.write_text("\n".join(lines_out), encoding="utf-8")


def copy_split(source_dir: Path, output_dir: Path, split: str) -> None:
    source_images = source_dir / split / "images"
    source_labels = source_dir / split / "labels"
    target_images = output_dir / split / "images"
    target_labels = output_dir / split / "labels"

    target_images.mkdir(parents=True, exist_ok=True)
    target_labels.mkdir(parents=True, exist_ok=True)

    for image_path in sorted(source_images.iterdir()):
        if not image_path.is_file():
            continue

        target_image = target_images / image_path.name
        shutil.copy2(image_path, target_image)

        source_label = source_labels / f"{image_path.stem}.txt"
        target_label = target_labels / f"{image_path.stem}.txt"
        rewrite_label_file(source_label, target_label)


def write_data_yaml(output_dir: Path, class_name: str) -> None:
    data = {
        "train": str((output_dir / "train" / "images").resolve()),
        "val": str((output_dir / "valid" / "images").resolve()),
        "test": str((output_dir / "test" / "images").resolve()),
        "nc": 1,
        "names": [class_name],
    }

    with (output_dir / "data.yaml").open("w", encoding="utf-8") as handle:
        yaml.safe_dump(data, handle, allow_unicode=True, sort_keys=False)


def main() -> None:
    args = parse_args()
    source_dir = Path(args.source).resolve()
    output_dir = Path(args.output).resolve()

    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    for split in SPLITS:
        copy_split(source_dir, output_dir, split)

    write_data_yaml(output_dir, args.class_name)
    print(f"Dataset convertido para classe unica em: {output_dir}")


if __name__ == "__main__":
    main()
