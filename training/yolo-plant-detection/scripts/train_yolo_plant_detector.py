from __future__ import annotations

import argparse
import json
from pathlib import Path

from ultralytics import YOLO


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Treina detector YOLO binario para plantas.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    training_cfg = config["training"]
    dataset_cfg = config["dataset"]
    outputs_cfg = config["outputs"]

    yolo_dir = Path(dataset_cfg["yolo_dir"])
    data_yaml = yolo_dir / "data.yaml"
    if not data_yaml.exists():
        raise FileNotFoundError(f"Ficheiro YOLO nao encontrado: {data_yaml}")

    artifacts_dir = Path(outputs_cfg["artifacts_dir"])
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    model = YOLO(training_cfg["model"])
    model.train(
        data=str(data_yaml),
        imgsz=int(training_cfg["imgsz"]),
        epochs=int(training_cfg["epochs"]),
        batch=int(training_cfg["batch"]),
        patience=int(training_cfg["patience"]),
        device=training_cfg.get("device", "cpu"),
        workers=int(training_cfg.get("workers", 4)),
        project=str(artifacts_dir),
        name=outputs_cfg["run_name"],
        exist_ok=True,
    )

    metrics = model.val(data=str(data_yaml))
    print(metrics)


if __name__ == "__main__":
    main()
