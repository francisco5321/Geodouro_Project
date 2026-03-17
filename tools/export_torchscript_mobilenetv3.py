import argparse
from pathlib import Path
from typing import Dict, Optional

import torch
from torch import nn
from torchvision.models import mobilenet_v3_small


def count_labels(labels_path: Path) -> int:
    if not labels_path.exists():
        raise FileNotFoundError(f"Labels file not found: {labels_path}")

    with labels_path.open("r", encoding="utf-8") as file:
        labels = [
            line.strip()
            for line in file.readlines()
            if line.strip() and not line.strip().startswith("#")
        ]

    if not labels:
        raise ValueError("No labels found. Add one label per line in species_labels.txt")

    return len(labels)


def extract_state_dict(checkpoint: object) -> Dict[str, torch.Tensor]:
    if isinstance(checkpoint, dict):
        for key in ("state_dict", "model_state_dict", "model", "net"):
            value = checkpoint.get(key)
            if isinstance(value, dict):
                return value

        if checkpoint and all(isinstance(value, torch.Tensor) for value in checkpoint.values()):
            return checkpoint

    raise ValueError(
        "Could not find state dict inside checkpoint. "
        "Expected dict with keys like state_dict/model_state_dict/model/net."
    )


def strip_known_prefixes(state_dict: Dict[str, torch.Tensor]) -> Dict[str, torch.Tensor]:
    cleaned = {}
    for key, value in state_dict.items():
        new_key = key
        if new_key.startswith("module."):
            new_key = new_key[len("module.") :]
        if new_key.startswith("model."):
            new_key = new_key[len("model.") :]
        cleaned[new_key] = value
    return cleaned


def build_model(num_classes: int) -> nn.Module:
    model = mobilenet_v3_small(weights=None)
    in_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(in_features, num_classes)
    return model


def try_load_script_module(input_path: Path) -> Optional[torch.jit.ScriptModule]:
    try:
        return torch.jit.load(str(input_path), map_location="cpu")
    except Exception:
        return None


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Export MobileNetV3 checkpoint to TorchScript for Android (PyTorch full runtime)."
    )
    parser.add_argument(
        "--input",
        default="mobilenetv3_small_best.pt",
        help="Input checkpoint path (.pt from training).",
    )
    parser.add_argument(
        "--labels",
        default="app/src/main/assets/species_labels.txt",
        help="Labels path used to infer number of classes.",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/assets/mobilenetv3_small_best.pt",
        help="Output TorchScript path (.pt) to be loaded by Android app.",
    )
    parser.add_argument(
        "--image-size",
        type=int,
        default=224,
        help="Input image size used by the model (default: 224).",
    )
    parser.add_argument(
        "--num-classes",
        type=int,
        default=None,
        help="Number of output classes. Overrides label counting when provided.",
    )

    args = parser.parse_args()

    input_path = Path(args.input)
    labels_path = Path(args.labels)
    output_path = Path(args.output)

    if not input_path.exists():
        raise FileNotFoundError(f"Model checkpoint not found: {input_path}")

    script_module = try_load_script_module(input_path)
    if script_module is not None:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        script_module.save(str(output_path))
        print(f"Input is already TorchScript. Saved copy to: {output_path}")
        return

    num_classes = args.num_classes if args.num_classes is not None else count_labels(labels_path)
    checkpoint = torch.load(str(input_path), map_location="cpu")

    if isinstance(checkpoint, nn.Module):
        model = checkpoint
        model.eval()
    else:
        state_dict = extract_state_dict(checkpoint)
        state_dict = strip_known_prefixes(state_dict)

        model = build_model(num_classes)
        load_result = model.load_state_dict(state_dict, strict=False)
        model.eval()

        if load_result.missing_keys:
            print("Warning: missing keys:", load_result.missing_keys)
        if load_result.unexpected_keys:
            print("Warning: unexpected keys:", load_result.unexpected_keys)

    example_input = torch.rand(1, 3, args.image_size, args.image_size)
    scripted = torch.jit.trace(model, example_input)
    scripted = torch.jit.freeze(scripted.eval())

    output_path.parent.mkdir(parents=True, exist_ok=True)
    scripted.save(str(output_path))

    print(f"TorchScript exported successfully to: {output_path}")


if __name__ == "__main__":
    main()
