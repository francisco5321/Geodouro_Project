from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
from typing import Callable

import pandas as pd
import torch
from PIL import Image
from torch import nn
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small
from tqdm import tqdm


IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


class CsvImageDataset(Dataset):
    def __init__(
        self,
        csv_path: Path,
        class_to_idx: dict[str, int],
        transform: Callable,
    ) -> None:
        self.rows = pd.read_csv(csv_path).to_dict("records")
        self.class_to_idx = class_to_idx
        self.transform = transform

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, int]:
        row = self.rows[index]
        image = Image.open(row["image_path"]).convert("RGB")
        label = self.class_to_idx[str(row["label"])]
        return self.transform(image), label


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def load_labels(splits_dir: Path) -> list[str]:
    labels_path = splits_dir / "labels.json"
    if labels_path.exists():
        return json.loads(labels_path.read_text(encoding="utf-8"))

    labels = set()
    for split_name in ("train.csv", "val.csv", "test.csv"):
        split_path = splits_dir / split_name
        if split_path.exists():
            labels.update(pd.read_csv(split_path)["label"].astype(str).tolist())
    if not labels:
        raise FileNotFoundError(f"No labels found in {splits_dir}")
    return sorted(labels)


def build_transforms(image_size: int) -> tuple[transforms.Compose, transforms.Compose]:
    train_transform = transforms.Compose(
        [
            transforms.Resize(int(image_size * 1.14)),
            transforms.RandomResizedCrop(image_size, scale=(0.75, 1.0)),
            transforms.RandomHorizontalFlip(),
            transforms.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15),
            transforms.ToTensor(),
            transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ]
    )
    eval_transform = transforms.Compose(
        [
            transforms.Resize(int(image_size * 1.14)),
            transforms.CenterCrop(image_size),
            transforms.ToTensor(),
            transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
        ]
    )
    return train_transform, eval_transform


def build_model(num_classes: int, dropout: float, freeze_features: bool) -> nn.Module:
    model = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
    model.classifier[2].p = dropout
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, num_classes)

    if freeze_features:
        for parameter in model.features.parameters():
            parameter.requires_grad = False

    return model


def set_feature_training(model: nn.Module, trainable: bool) -> None:
    for parameter in model.features.parameters():
        parameter.requires_grad = trainable


@torch.no_grad()
def evaluate(model: nn.Module, loader: DataLoader, criterion: nn.Module, device: torch.device) -> dict:
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0

    for images, labels in loader:
        images = images.to(device)
        labels = labels.to(device)
        logits = model(images)
        loss = criterion(logits, labels)

        total_loss += loss.item() * images.size(0)
        correct += (logits.argmax(dim=1) == labels).sum().item()
        total += images.size(0)

    return {
        "loss": total_loss / max(total, 1),
        "acc": correct / max(total, 1),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Train MobileNetV3-Small from CSV splits.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]
    training_cfg = config["training"]
    outputs_cfg = config["outputs"]

    splits_dir = Path(dataset_cfg["splits_dir"])
    artifacts_dir = Path(outputs_cfg["artifacts_dir"])
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    labels = load_labels(splits_dir)
    class_to_idx = {label: index for index, label in enumerate(labels)}
    image_size = int(dataset_cfg["image_size"])

    train_transform, eval_transform = build_transforms(image_size)
    train_ds = CsvImageDataset(splits_dir / "train.csv", class_to_idx, train_transform)
    val_ds = CsvImageDataset(splits_dir / "val.csv", class_to_idx, eval_transform)
    test_ds = CsvImageDataset(splits_dir / "test.csv", class_to_idx, eval_transform)

    batch_size = int(training_cfg["batch_size"])
    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, num_workers=2)
    val_loader = DataLoader(val_ds, batch_size=batch_size, shuffle=False, num_workers=2)
    test_loader = DataLoader(test_ds, batch_size=batch_size, shuffle=False, num_workers=2)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = build_model(
        num_classes=len(labels),
        dropout=float(training_cfg["dropout"]),
        freeze_features=bool(training_cfg["freeze_features"]),
    ).to(device)

    criterion = nn.CrossEntropyLoss(label_smoothing=float(training_cfg["label_smoothing"]))
    optimizer = torch.optim.AdamW(
        (parameter for parameter in model.parameters() if parameter.requires_grad),
        lr=float(training_cfg["learning_rate"]),
        weight_decay=float(training_cfg["weight_decay"]),
    )

    best_state = copy.deepcopy(model.state_dict())
    best_val_acc = -1.0
    best_epoch = 0
    history = []
    epochs = int(training_cfg["epochs"])
    freeze_epochs = int(training_cfg.get("freeze_epochs", 0))

    for epoch in range(1, epochs + 1):
        if bool(training_cfg["freeze_features"]) and epoch == freeze_epochs + 1:
            set_feature_training(model, trainable=True)
            optimizer = torch.optim.AdamW(
                model.parameters(),
                lr=float(training_cfg["learning_rate"]) * float(training_cfg["unfreeze_lr_scale"]),
                weight_decay=float(training_cfg["weight_decay"]),
            )

        model.train()
        running_loss = 0.0
        running_correct = 0
        running_total = 0

        progress = tqdm(train_loader, desc=f"Epoch {epoch}/{epochs}", leave=False)
        for images, labels_tensor in progress:
            images = images.to(device)
            labels_tensor = labels_tensor.to(device)

            optimizer.zero_grad(set_to_none=True)
            logits = model(images)
            loss = criterion(logits, labels_tensor)
            loss.backward()
            optimizer.step()

            running_loss += loss.item() * images.size(0)
            running_correct += (logits.argmax(dim=1) == labels_tensor).sum().item()
            running_total += images.size(0)
            progress.set_postfix(loss=running_loss / max(running_total, 1))

        train_metrics = {
            "loss": running_loss / max(running_total, 1),
            "acc": running_correct / max(running_total, 1),
        }
        val_metrics = evaluate(model, val_loader, criterion, device)
        history.append({"epoch": epoch, "train": train_metrics, "val": val_metrics})

        print(
            f"Epoch {epoch:02d}/{epochs} "
            f"train_acc={train_metrics['acc']:.4f} val_acc={val_metrics['acc']:.4f}"
        )

        if val_metrics["acc"] > best_val_acc:
            best_val_acc = val_metrics["acc"]
            best_epoch = epoch
            best_state = copy.deepcopy(model.state_dict())

            checkpoint = {
                "model_state_dict": best_state,
                "class_to_idx": class_to_idx,
                "idx_to_label": {index: label for label, index in class_to_idx.items()},
                "num_classes": len(labels),
                "best_epoch": best_epoch,
                "best_val_acc": best_val_acc,
                "hyperparameters": training_cfg,
                "image_size": image_size,
            }
            torch.save(checkpoint, artifacts_dir / outputs_cfg["checkpoint_name"])

            run_config = {
                "checkpoint_path": str((artifacts_dir / outputs_cfg["checkpoint_name"]).resolve()),
                "num_classes": len(labels),
                "best_epoch": best_epoch,
                "best_val_acc": best_val_acc,
                "hyperparameters": training_cfg,
                "class_to_idx": class_to_idx,
            }
            (artifacts_dir / "run_config.json").write_text(
                json.dumps(run_config, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    model.load_state_dict(best_state)
    test_metrics = evaluate(model, test_loader, criterion, device)
    (artifacts_dir / "history.json").write_text(
        json.dumps({"history": history, "test": test_metrics}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Best epoch: {best_epoch} | best val_acc={best_val_acc:.4f}")
    print(f"Test acc={test_metrics['acc']:.4f}")
    print(f"Checkpoint: {(artifacts_dir / outputs_cfg['checkpoint_name']).resolve()}")


if __name__ == "__main__":
    main()
