from __future__ import annotations

import argparse
import csv
import json
import random
from pathlib import Path
from typing import Any

import cv2
import pandas as pd
from ultralytics import YOLO


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Revê previsoes do detector YOLO em imagens reais e gera previews."
    )
    parser.add_argument("--weights", required=True, help="Caminho para o ficheiro .pt do detector.")
    parser.add_argument(
        "--output-dir",
        default="artifacts/yolo_prediction_review",
        help="Diretorio onde guardar previews e relatorios.",
    )
    parser.add_argument("--conf", type=float, default=0.25, help="Threshold de confiança.")
    parser.add_argument("--imgsz", type=int, default=640, help="Tamanho de inferencia.")
    parser.add_argument("--max-images", type=int, default=120, help="Numero maximo de imagens a rever.")
    parser.add_argument("--seed", type=int, default=42, help="Seed para amostragem.")
    parser.add_argument(
        "--batch-names",
        nargs="*",
        default=["batch_001", "batch_002"],
        help="Batches de anotação a usar na revisão.",
    )
    parser.add_argument(
        "--annotation-batches-dir",
        default="data/annotation_batches",
        help="Diretorio base dos annotation batches.",
    )
    parser.add_argument(
        "--annotations-dir",
        default="data/annotations",
        help="Diretorio com labels YOLO revistas.",
    )
    return parser.parse_args()


def normalize_host_path(path_value: str) -> Path:
    normalized = path_value.replace("\\", "/").strip()
    if len(normalized) >= 3 and normalized[1:3] == ":/":
        drive_letter = normalized[0].lower()
        remainder = normalized[3:]
        return Path("/mnt") / drive_letter / remainder
    return Path(normalized)


def choose_rows(df: pd.DataFrame, max_images: int, seed: int) -> pd.DataFrame:
    if max_images <= 0 or len(df) <= max_images:
        return df.copy()

    positives = df[df["has_gt_boxes"]]
    negatives = df[~df["has_gt_boxes"]]
    target_positives = min(len(positives), max_images // 2)
    target_negatives = min(len(negatives), max_images - target_positives)

    if target_positives + target_negatives < max_images:
        remaining = max_images - target_positives - target_negatives
        extra_pool = df.drop(
            positives.sample(n=target_positives, random_state=seed).index if target_positives else [],
            errors="ignore",
        )
        extra_pool = extra_pool.drop(
            negatives.sample(n=target_negatives, random_state=seed).index if target_negatives else [],
            errors="ignore",
        )
        extra = extra_pool.sample(n=min(len(extra_pool), remaining), random_state=seed) if len(extra_pool) else extra_pool
    else:
        extra = df.iloc[0:0]

    selected_parts = []
    if target_positives:
        selected_parts.append(positives.sample(n=target_positives, random_state=seed))
    if target_negatives:
        selected_parts.append(negatives.sample(n=target_negatives, random_state=seed))
    if len(extra):
        selected_parts.append(extra)

    selected = pd.concat(selected_parts).drop_duplicates(subset=["image_path"]).sample(frac=1.0, random_state=seed)
    return selected.reset_index(drop=True)


def load_review_rows(annotation_batches_dir: Path, annotations_dir: Path, batch_names: list[str]) -> pd.DataFrame:
    rows: list[dict[str, Any]] = []

    for batch_name in batch_names:
        metadata_path = annotation_batches_dir / batch_name / "metadata.csv"
        if not metadata_path.exists():
            raise FileNotFoundError(f"Metadata não encontrada para batch '{batch_name}': {metadata_path}")

        metadata_df = pd.read_csv(metadata_path)
        for row in metadata_df.to_dict("records"):
            image_path = normalize_host_path(str(row["resolved_source_path"])).resolve()
            label_name = Path(str(row["annotation_label_path"]).replace("\\", "/")).name
            reviewed_label = annotations_dir / label_name
            if not reviewed_label.exists():
                continue

            rows.append(
                {
                    "batch_name": batch_name,
                    "image_path": str(image_path),
                    "image_name": image_path.name,
                    "source_kind": row.get("source_kind", ""),
                    "scientific_name": row.get("scientific_name", ""),
                    "has_gt_boxes": reviewed_label.stat().st_size > 0,
                    "label_path": str(reviewed_label),
                }
            )

    df = pd.DataFrame(rows)
    if df.empty:
        raise RuntimeError("Nenhuma imagem revista encontrada para avaliacao.")

    return df.drop_duplicates(subset=["image_path"]).reset_index(drop=True)


def draw_predictions(image, boxes, conf_threshold: float):
    preview = image.copy()
    kept = []

    for box in boxes:
        confidence = float(box.conf.item())
        if confidence < conf_threshold:
            continue

        x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
        kept.append((x1, y1, x2, y2, confidence))
        cv2.rectangle(preview, (x1, y1), (x2, y2), (40, 180, 60), 2)
        cv2.putText(
            preview,
            f"plant {confidence:.2f}",
            (x1, max(24, y1 - 8)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.65,
            (40, 180, 60),
            2,
            cv2.LINE_AA,
        )

    return preview, kept


def write_summary(output_dir: Path, records: list[dict[str, Any]]) -> None:
    summary_path = output_dir / "summary.json"
    csv_path = output_dir / "predictions.csv"

    positives = [r for r in records if r["has_gt_boxes"]]
    negatives = [r for r in records if not r["has_gt_boxes"]]
    tp = sum(1 for r in positives if r["predicted_any"])
    fn = sum(1 for r in positives if not r["predicted_any"])
    fp = sum(1 for r in negatives if r["predicted_any"])
    tn = sum(1 for r in negatives if not r["predicted_any"])

    summary = {
        "total_images": len(records),
        "positives": len(positives),
        "negatives": len(negatives),
        "true_positive_images": tp,
        "false_negative_images": fn,
        "false_positive_images": fp,
        "true_negative_images": tn,
        "positive_image_recall": tp / len(positives) if positives else None,
        "negative_image_specificity": tn / len(negatives) if negatives else None,
        "confidence_threshold": records[0]["confidence_threshold"] if records else None,
    }
    summary_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")

    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "batch_name",
                "image_name",
                "scientific_name",
                "source_kind",
                "has_gt_boxes",
                "predicted_any",
                "prediction_count",
                "best_confidence",
                "confidence_threshold",
                "image_path",
                "preview_path",
            ],
        )
        writer.writeheader()
        writer.writerows(records)


def main() -> None:
    args = parse_args()
    annotation_batches_dir = Path(args.annotation_batches_dir)
    annotations_dir = Path(args.annotations_dir)
    output_dir = Path(args.output_dir)
    previews_dir = output_dir / "previews"
    false_positives_dir = output_dir / "false_positives"
    false_negatives_dir = output_dir / "false_negatives"

    previews_dir.mkdir(parents=True, exist_ok=True)
    false_positives_dir.mkdir(parents=True, exist_ok=True)
    false_negatives_dir.mkdir(parents=True, exist_ok=True)

    review_df = load_review_rows(annotation_batches_dir, annotations_dir, args.batch_names)
    review_df = choose_rows(review_df, max_images=args.max_images, seed=args.seed)

    model = YOLO(args.weights)
    records: list[dict[str, Any]] = []

    for idx, row in enumerate(review_df.to_dict("records"), start=1):
        image_path = Path(str(row["image_path"]))
        if image_path.suffix.lower() not in IMAGE_EXTENSIONS or not image_path.exists():
            continue

        image = cv2.imread(str(image_path))
        if image is None:
            continue

        result = model.predict(source=str(image_path), conf=args.conf, imgsz=args.imgsz, verbose=False)[0]
        preview, kept = draw_predictions(image, result.boxes, args.conf)
        has_gt_boxes = bool(row["has_gt_boxes"])
        predicted_any = len(kept) > 0
        best_confidence = max((item[4] for item in kept), default=0.0)

        preview_name = f"{idx:04d}_{image_path.name}"
        preview_path = previews_dir / preview_name
        cv2.imwrite(str(preview_path), preview)

        if predicted_any and not has_gt_boxes:
            cv2.imwrite(str(false_positives_dir / preview_name), preview)
        elif has_gt_boxes and not predicted_any:
            cv2.imwrite(str(false_negatives_dir / preview_name), preview)

        records.append(
            {
                "batch_name": row["batch_name"],
                "image_name": image_path.name,
                "scientific_name": row["scientific_name"],
                "source_kind": row["source_kind"],
                "has_gt_boxes": has_gt_boxes,
                "predicted_any": predicted_any,
                "prediction_count": len(kept),
                "best_confidence": round(best_confidence, 6),
                "image_path": str(image_path),
                "preview_path": str(preview_path),
                "confidence_threshold": args.conf,
            }
        )

    write_summary(output_dir, records)
    print(f"Revisao concluida em: {output_dir.resolve()}")
    print(f"Imagens analisadas: {len(records)}")


if __name__ == "__main__":
    main()
