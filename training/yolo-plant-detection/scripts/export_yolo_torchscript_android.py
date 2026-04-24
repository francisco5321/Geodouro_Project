from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

from ultralytics import YOLO


DEFAULT_APP_ASSET_NAME = "yolo_plant_detector_android.torchscript"


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Exporta o detector YOLO para TorchScript Android.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    parser.add_argument(
        "--weights",
        type=Path,
        default=None,
        help="Pesos .pt a exportar. Se omitido, usa o best.pt do run configurado.",
    )
    parser.add_argument("--copy-to-app", action="store_true")
    parser.add_argument("--asset-name", type=str, default=DEFAULT_APP_ASSET_NAME)
    args = parser.parse_args()

    config = load_config(args.config)
    outputs_cfg = config["outputs"]
    training_cfg = config["training"]
    artifacts_dir = Path(outputs_cfg["artifacts_dir"])
    run_name = outputs_cfg["run_name"]
    weights_path = args.weights or (artifacts_dir / run_name / "weights" / "best.pt")
    imgsz = int(training_cfg.get("imgsz", 640))

    model = YOLO(str(weights_path))
    exported_path = Path(
        model.export(
            format="torchscript",
            imgsz=imgsz,
            optimize=True,
            nms=False,
            device="cpu",
        )
    )

    print(f"TorchScript exportado em: {exported_path.resolve()}")

    if args.copy_to_app:
        repo_root = Path(__file__).resolve().parents[3]
        assets_dir = repo_root / "app" / "src" / "main" / "assets"
        assets_dir.mkdir(parents=True, exist_ok=True)
        target_path = assets_dir / args.asset_name
        shutil.copy2(exported_path, target_path)
        print(f"Modelo copiado para assets: {target_path.resolve()}")


if __name__ == "__main__":
    main()
