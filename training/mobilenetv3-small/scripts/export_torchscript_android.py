from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any, Mapping

import torch
from torch import nn
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def extract_state_dict(checkpoint: Any) -> dict[str, torch.Tensor]:
    if isinstance(checkpoint, nn.Module):
        return checkpoint.state_dict()

    if isinstance(checkpoint, Mapping):
        for key in ("model_state_dict", "state_dict"):
            value = checkpoint.get(key)
            if isinstance(value, Mapping):
                return dict(value)
        if checkpoint and all(isinstance(value, torch.Tensor) for value in checkpoint.values()):
            return dict(checkpoint)

    raise ValueError("Could not extract state_dict from checkpoint.")


def infer_num_classes(checkpoint: Any, state_dict: Mapping[str, torch.Tensor]) -> int:
    if isinstance(checkpoint, Mapping):
        if isinstance(checkpoint.get("num_classes"), int):
            return int(checkpoint["num_classes"])
        class_to_idx = checkpoint.get("class_to_idx")
        if isinstance(class_to_idx, Mapping):
            return len(class_to_idx)
    return int(state_dict["classifier.3.weight"].shape[0])


def infer_dropout(checkpoint: Any, default: float) -> float:
    if isinstance(checkpoint, Mapping):
        hp = checkpoint.get("hyperparameters")
        if isinstance(hp, Mapping) and isinstance(hp.get("dropout"), (float, int)):
            return float(hp["dropout"])
    return default


def infer_image_size(checkpoint: Any, default: int) -> int:
    if isinstance(checkpoint, Mapping):
        if isinstance(checkpoint.get("image_size"), int):
            return int(checkpoint["image_size"])
    return default


def build_labels(checkpoint: Any, num_classes: int) -> list[str]:
    labels = [""] * num_classes
    if isinstance(checkpoint, Mapping):
        idx_to_label = checkpoint.get("idx_to_label")
        if isinstance(idx_to_label, Mapping):
            for key, value in idx_to_label.items():
                index = int(key)
                if 0 <= index < num_classes:
                    labels[index] = str(value)
        class_to_idx = checkpoint.get("class_to_idx")
        if isinstance(class_to_idx, Mapping):
            for label, index_value in class_to_idx.items():
                index = int(index_value)
                if 0 <= index < num_classes and not labels[index]:
                    labels[index] = str(label)

    return [label if label else str(index) for index, label in enumerate(labels)]


def build_model(num_classes: int, dropout: float) -> nn.Module:
    model = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
    model.classifier[2].p = dropout
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, num_classes)
    return model


def main() -> None:
    parser = argparse.ArgumentParser(description="Export MobileNetV3-Small checkpoint to Android TorchScript.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    parser.add_argument("--copy-to-app", action="store_true")
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]
    training_cfg = config["training"]
    outputs_cfg = config["outputs"]
    artifacts_dir = Path(outputs_cfg["artifacts_dir"])

    checkpoint_path = artifacts_dir / outputs_cfg["checkpoint_name"]
    checkpoint = torch.load(checkpoint_path, map_location="cpu")
    state_dict = extract_state_dict(checkpoint)
    num_classes = infer_num_classes(checkpoint, state_dict)
    image_size = infer_image_size(checkpoint, int(dataset_cfg["image_size"]))
    dropout = infer_dropout(checkpoint, float(training_cfg["dropout"]))

    model = build_model(num_classes=num_classes, dropout=dropout)
    model.load_state_dict(state_dict, strict=True)
    model.eval()

    example_input = torch.randn(1, 3, image_size, image_size)
    scripted = torch.jit.trace(model, example_input, strict=False)

    android_model_path = artifacts_dir / outputs_cfg["android_model_name"]
    labels_path = artifacts_dir / outputs_cfg["labels_name"]
    scripted.save(str(android_model_path))
    labels_path.write_text("\n".join(build_labels(checkpoint, num_classes)) + "\n", encoding="utf-8")

    print(f"Android model saved to: {android_model_path.resolve()}")
    print(f"Labels saved to: {labels_path.resolve()}")

    if args.copy_to_app:
        repo_root = Path(__file__).resolve().parents[3]
        assets_dir = repo_root / "app" / "src" / "main" / "assets"
        assets_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(android_model_path, assets_dir / outputs_cfg["android_model_name"])
        shutil.copy2(labels_path, assets_dir / outputs_cfg["labels_name"])
        print(f"Copied model and labels to: {assets_dir}")


if __name__ == "__main__":
    main()
