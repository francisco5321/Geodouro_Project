from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np
import pandas as pd


def load_config(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def clamp_rect(x: int, y: int, w: int, h: int, image_width: int, image_height: int) -> tuple[int, int, int, int]:
    x = max(0, min(x, image_width - 1))
    y = max(0, min(y, image_height - 1))
    w = max(1, min(w, image_width - x))
    h = max(1, min(h, image_height - y))
    return x, y, w, h


def resize_for_processing(image_bgr: np.ndarray, max_dimension: int) -> tuple[np.ndarray, float]:
    height, width = image_bgr.shape[:2]
    longest_side = max(height, width)
    if longest_side <= max_dimension:
        return image_bgr, 1.0

    scale = max_dimension / float(longest_side)
    resized = cv2.resize(
        image_bgr,
        (max(1, int(width * scale)), max(1, int(height * scale))),
        interpolation=cv2.INTER_AREA,
    )
    return resized, scale


def component_score(
    stats: np.ndarray,
    centroid: np.ndarray,
    image_width: int,
    image_height: int,
    center_weight: float,
) -> float:
    x, y, w, h, area = stats.tolist()
    if area <= 0:
        return -1.0

    area_ratio = area / float(image_width * image_height)
    cx, cy = centroid.tolist()
    center_distance = np.sqrt(((cx / image_width) - 0.5) ** 2 + ((cy / image_height) - 0.5) ** 2)
    center_score = 1.0 - min(1.0, center_distance / 0.707)
    aspect_ratio = w / max(h, 1)
    aspect_penalty = 0.0 if 0.2 <= aspect_ratio <= 5.0 else 0.15
    return area_ratio + (center_weight * center_score) - aspect_penalty


def build_vegetation_mask(image_bgr: np.ndarray) -> np.ndarray:
    hsv = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2HSV)
    lower_green = np.array([25, 30, 20], dtype=np.uint8)
    upper_green = np.array([95, 255, 255], dtype=np.uint8)
    green_mask = cv2.inRange(hsv, lower_green, upper_green)

    rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB).astype(np.float32)
    r = rgb[:, :, 0]
    g = rgb[:, :, 1]
    b = rgb[:, :, 2]
    excess_green = (2 * g) - r - b
    excess_green = cv2.normalize(excess_green, None, 0, 255, cv2.NORM_MINMAX).astype(np.uint8)
    _, exg_mask = cv2.threshold(excess_green, 110, 255, cv2.THRESH_BINARY)

    mask = cv2.bitwise_or(green_mask, exg_mask)
    kernel = np.ones((7, 7), np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
    return mask


def build_grabcut_mask(image_bgr: np.ndarray) -> np.ndarray:
    height, width = image_bgr.shape[:2]
    mask = np.zeros((height, width), np.uint8)
    rect = (
        max(1, int(width * 0.08)),
        max(1, int(height * 0.08)),
        max(2, int(width * 0.84)),
        max(2, int(height * 0.84)),
    )
    bgd_model = np.zeros((1, 65), np.float64)
    fgd_model = np.zeros((1, 65), np.float64)

    try:
        cv2.grabCut(image_bgr, mask, rect, bgd_model, fgd_model, 3, cv2.GC_INIT_WITH_RECT)
    except cv2.error:
        return np.zeros((height, width), dtype=np.uint8)

    mask = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    kernel = np.ones((7, 7), np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
    return mask


def select_best_bbox(mask: np.ndarray, image_width: int, image_height: int, center_weight: float) -> tuple[int, int, int, int] | None:
    num_labels, labels, stats, centroids = cv2.connectedComponentsWithStats(mask, connectivity=8)
    if num_labels <= 1:
        return None

    best_index = None
    best_score = -1.0
    for index in range(1, num_labels):
        score = component_score(stats[index], centroids[index], image_width, image_height, center_weight)
        if score > best_score:
            best_score = score
            best_index = index

    if best_index is None:
        return None

    x, y, w, h, _ = stats[best_index].tolist()
    return clamp_rect(x, y, w, h, image_width, image_height)


def bbox_to_yolo(x: int, y: int, w: int, h: int, image_width: int, image_height: int) -> str:
    x_center = (x + (w / 2.0)) / image_width
    y_center = (y + (h / 2.0)) / image_height
    width_norm = w / image_width
    height_norm = h / image_height
    return f"0 {x_center:.6f} {y_center:.6f} {width_norm:.6f} {height_norm:.6f}"


def scale_bbox_back(bbox: tuple[int, int, int, int] | None, scale: float, image_width: int, image_height: int) -> tuple[int, int, int, int] | None:
    if bbox is None:
        return None
    if scale == 1.0:
        return bbox

    x, y, w, h = bbox
    inv = 1.0 / scale
    scaled = (
        int(round(x * inv)),
        int(round(y * inv)),
        int(round(w * inv)),
        int(round(h * inv)),
    )
    return clamp_rect(*scaled, image_width=image_width, image_height=image_height)


def draw_preview(image_bgr: np.ndarray, bbox: tuple[int, int, int, int] | None, output_path: Path) -> None:
    preview = image_bgr.copy()
    if bbox is not None:
        x, y, w, h = bbox
        cv2.rectangle(preview, (x, y), (x + w, y + h), (0, 255, 0), 3)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(output_path), preview)


def main() -> None:
    parser = argparse.ArgumentParser(description="Gera pre-anotacoes YOLO heuristicas para um batch.")
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    parser.add_argument("--batch-name", type=str, default=None)
    args = parser.parse_args()

    config = load_config(args.config)
    dataset_cfg = config["dataset"]
    pre_cfg = config["preannotation"]

    batch_name = args.batch_name or pre_cfg["batch_name"]
    batch_dir = Path(dataset_cfg["annotation_batches_dir"]) / batch_name
    images_dir = batch_dir / "images"
    labels_dir = batch_dir / "labels"
    metadata_path = batch_dir / "metadata.csv"
    previews_dir = batch_dir / "previews"

    if not metadata_path.exists():
        raise FileNotFoundError(f"Metadata do batch nao encontrado: {metadata_path}")

    labels_dir.mkdir(parents=True, exist_ok=True)
    if bool(pre_cfg.get("save_previews", True)):
        previews_dir.mkdir(parents=True, exist_ok=True)

    metadata_df = pd.read_csv(metadata_path)
    overwrite = bool(pre_cfg.get("overwrite_existing_labels", False))
    max_images = int(pre_cfg.get("max_images", 0))
    processing_max_dimension = int(pre_cfg.get("processing_max_dimension", 1024))
    min_mask_area_ratio = float(pre_cfg.get("min_mask_area_ratio", 0.015))
    min_box_area_ratio = float(pre_cfg.get("min_box_area_ratio", 0.02))
    center_weight = float(pre_cfg.get("center_weight", 0.35))

    if max_images > 0:
        metadata_df = metadata_df.head(max_images)

    generated = 0
    empty = 0
    skipped = 0
    processed = 0
    save_previews = bool(pre_cfg.get("save_previews", True))

    for row in metadata_df.to_dict("records"):
        image_path = Path(row["annotation_image_path"])
        label_path = Path(row["annotation_label_path"])
        preview_path = previews_dir / image_path.name

        if not image_path.exists():
            skipped += 1
            continue

        already_preannotated = preview_path.exists() if save_previews else False
        if not overwrite and (already_preannotated or (label_path.exists() and label_path.read_text(encoding="utf-8").strip())):
            skipped += 1
            continue

        image_bgr = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if image_bgr is None:
            skipped += 1
            continue

        image_height, image_width = image_bgr.shape[:2]
        processing_image, scale = resize_for_processing(image_bgr, processing_max_dimension)
        processing_height, processing_width = processing_image.shape[:2]

        vegetation_mask = build_vegetation_mask(processing_image)
        vegetation_area_ratio = float(np.count_nonzero(vegetation_mask)) / float(processing_width * processing_height)
        vegetation_bbox = select_best_bbox(vegetation_mask, processing_width, processing_height, center_weight)

        combined_mask = vegetation_mask
        mask_area_ratio = vegetation_area_ratio
        bbox = vegetation_bbox

        # GrabCut is much slower than the vegetation heuristic, so only use it
        # when the initial mask is too weak to propose a meaningful crop.
        if bbox is None or vegetation_area_ratio < min_mask_area_ratio:
            grabcut_mask = build_grabcut_mask(processing_image)
            combined_mask = cv2.bitwise_or(vegetation_mask, grabcut_mask)
            mask_area_ratio = float(np.count_nonzero(combined_mask)) / float(processing_width * processing_height)
            bbox = select_best_bbox(combined_mask, processing_width, processing_height, center_weight)

        bbox = scale_bbox_back(bbox, scale, image_width, image_height)

        yolo_line = ""
        if bbox is not None and mask_area_ratio >= min_mask_area_ratio:
            x, y, w, h = bbox
            box_area_ratio = (w * h) / float(image_width * image_height)
            if box_area_ratio >= min_box_area_ratio:
                yolo_line = bbox_to_yolo(x, y, w, h, image_width, image_height)

        label_path.write_text(yolo_line + ("\n" if yolo_line else ""), encoding="utf-8")
        if save_previews:
            draw_preview(image_bgr, bbox if yolo_line else None, preview_path)

        processed += 1
        if yolo_line:
            generated += 1
        else:
            empty += 1

    summary = {
        "batch_name": batch_name,
        "generated_boxes": generated,
        "empty_labels": empty,
        "skipped": skipped,
        "processed": processed,
        "overwrite_existing_labels": overwrite,
    }
    (batch_dir / "preannotation_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Pre-anotacao concluida em: {batch_dir.resolve()}")
    print(f"Caixas geradas: {generated}")
    print(f"Labels vazias: {empty}")
    print(f"Ignoradas: {skipped}")


if __name__ == "__main__":
    main()
