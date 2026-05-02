from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
from dataclasses import asdict, dataclass
from io import StringIO
from pathlib import Path
from typing import Any


GENUS_ONLY_PATTERN = re.compile(r"^[A-Z][a-zA-Z-]+$")


@dataclass
class SyncResult:
    observation_id: int
    source_label: str
    label: str
    image_path: str
    remote_path: str | None
    local_path: str | None
    status: str
    note: str = ""


def load_config(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def build_ssh_options(server_cfg: dict[str, Any], scp: bool) -> list[str]:
    options: list[str] = ["-o", "BatchMode=yes", "-o", "ConnectTimeout=8"]
    identity_file = str(server_cfg.get("ssh_identity_file", "")).strip()
    ssh_port = server_cfg.get("ssh_port")

    if ssh_port:
        options.extend(["-P" if scp else "-p", str(ssh_port)])
    if identity_file:
        options.extend(["-i", identity_file])

    return options


def load_reference_labels(config: dict[str, Any]) -> list[str]:
    labels_path = Path(config["dataset"]["processed_dir"]) / "splits" / "labels.json"
    if not labels_path.exists():
        return []
    return json.loads(labels_path.read_text(encoding="utf-8"))


def canonicalize_label(label: str, reference_labels: list[str]) -> tuple[str, str]:
    normalized = " ".join(label.strip().split())
    if not normalized:
        return normalized, "empty"
    if normalized in reference_labels:
        return normalized, "exact"
    if GENUS_ONLY_PATTERN.match(normalized):
        genus_matches = [candidate for candidate in reference_labels if candidate.startswith(normalized + " ")]
        if len(genus_matches) == 1:
            return genus_matches[0], "genus_unique_fallback"
    return normalized, "unchanged"


def sanitize_filename_component(value: str) -> str:
    return re.sub(r'[<>:"/\\|?*]+', "_", value).strip().rstrip(".")


def query_labeled_images(server_cfg: dict[str, Any], ssh_options: list[str]) -> list[tuple[int, str, str]]:
    remote_db_host = str(server_cfg.get("remote_db_host", "127.0.0.1")).strip()
    remote_db_port = str(server_cfg.get("remote_db_port", "5432")).strip()
    remote_db_name = str(server_cfg.get("remote_db_name", "geodouro")).strip()
    remote_db_user = str(server_cfg.get("remote_db_user", "postgres")).strip()
    remote_db_password = str(server_cfg.get("remote_db_password", "")).strip()
    remote_sudo_password = str(server_cfg.get("remote_sudo_password", "")).strip()
    ssh_host = str(server_cfg["ssh_host"]).strip()
    query = """
        SELECT
            o.observation_id,
            COALESCE(
                NULLIF(btrim(o.enriched_scientific_name), ''),
                NULLIF(btrim(o.predicted_scientific_name), ''),
                NULLIF(btrim(ps.scientific_name), '')
            ) AS label,
            oi.image_path
        FROM observation o
        LEFT JOIN plant_species ps ON ps.plant_species_id = o.plant_species_id
        INNER JOIN observation_image oi ON oi.observation_id = o.observation_id
        WHERE oi.image_path IS NOT NULL
          AND btrim(oi.image_path) <> ''
          AND COALESCE(
                NULLIF(btrim(o.enriched_scientific_name), ''),
                NULLIF(btrim(o.predicted_scientific_name), ''),
                NULLIF(btrim(ps.scientific_name), '')
          ) IS NOT NULL
        ORDER BY o.observation_id ASC, oi.observation_image_id ASC
    """
    if remote_sudo_password:
        remote_command = (
            f"printf '%s\\n' '{remote_sudo_password}' | "
            + "sudo -S -u postgres "
            + f"psql -d '{remote_db_name}' -F $'\\t' -A -q -t -X -f -"
        )
    else:
        remote_command = (
            "export PGPASSWORD="
            + f"'{remote_db_password}'; "
            + f"psql -h '{remote_db_host}' -p '{remote_db_port}' -U '{remote_db_user}' -d '{remote_db_name}' -F $'\\t' -A -q -t -X -f -"
        )
    completed = subprocess.run(
        ["ssh", *ssh_options, ssh_host, remote_command],
        input=query,
        text=True,
        capture_output=True,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or "Failed to query remote PostgreSQL database over SSH.")

    rows: list[tuple[int, str, str]] = []
    reader = csv.reader(StringIO(completed.stdout), delimiter="\t")
    for row in reader:
        if len(row) != 3:
            continue
        observation_id, label, image_path = row
        rows.append((int(observation_id), str(label), str(image_path)))
    return rows


def run_ssh_capture(host: str, ssh_options: list[str], command: str) -> str:
    completed = subprocess.run(
        ["ssh", *ssh_options, host, command],
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        return ""
    return completed.stdout.strip()


def resolve_remote_path(
    host: str,
    remote_root: str,
    ssh_options: list[str],
    observation_id: int,
    relative_image_path: str,
) -> str | None:
    relative_path = relative_image_path.strip().lstrip("/\\").replace("\\", "/")
    if not relative_path:
        return None

    exact_remote_path = f"{remote_root.rstrip('/')}/{relative_path}"
    exact_match = run_ssh_capture(
        host,
        ssh_options,
        f"test -f '{exact_remote_path}' && printf '%s' '{exact_remote_path}'",
    )
    if exact_match:
        return exact_match

    basename = Path(relative_path).name
    parts = [part for part in relative_path.split("/") if part]
    candidate_patterns = {basename}
    if len(parts) == 2 and parts[1].startswith("image-"):
        candidate_patterns.add(f"{parts[0]}-{parts[1]}")

    escaped_patterns = " -o ".join(f"-name '{pattern}'" for pattern in sorted(candidate_patterns))
    found_in_observation_dir = run_ssh_capture(
        host,
        ssh_options,
        f"find '{remote_root.rstrip('/')}' -type f \\( {escaped_patterns} \\) | grep '/{observation_id}/' | head -n 1",
    )
    if found_in_observation_dir:
        return found_in_observation_dir.splitlines()[0].strip()

    found_anywhere = run_ssh_capture(
        host,
        ssh_options,
        f"find '{remote_root.rstrip('/')}' -type f \\( {escaped_patterns} \\) | head -n 1",
    )
    if found_anywhere:
        return found_anywhere.splitlines()[0].strip()

    return None


def copy_remote_file(host: str, scp_options: list[str], remote_path: str, destination: Path) -> bool:
    destination.parent.mkdir(parents=True, exist_ok=True)
    completed = subprocess.run(
        ["scp", *scp_options, f"{host}:{remote_path}", str(destination)],
        check=False,
        capture_output=True,
        text=True,
    )
    return completed.returncode == 0


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Download labeled observation images from the GeoDouro server into data/raw/<label>/."
    )
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    parser.add_argument(
        "--report-path",
        type=Path,
        default=Path("data/processed/project_server_sync_report.json"),
    )
    args = parser.parse_args()

    config = load_config(args.config)
    server_cfg = config["server"]
    dataset_cfg = config["dataset"]
    host = str(server_cfg["ssh_host"]).strip()
    remote_root = str(server_cfg["remote_images_path"]).strip().rstrip("/")
    raw_dir = Path(dataset_cfg["raw_dir"])
    raw_dir.mkdir(parents=True, exist_ok=True)
    args.report_path.parent.mkdir(parents=True, exist_ok=True)

    reference_labels = load_reference_labels(config)
    ssh_options = build_ssh_options(server_cfg, scp=False)
    scp_options = build_ssh_options(server_cfg, scp=True)

    results: list[SyncResult] = []
    copied = 0

    for observation_id, source_label, image_path in query_labeled_images(server_cfg, ssh_options):
        label, label_mapping = canonicalize_label(source_label, reference_labels)
        remote_path = resolve_remote_path(host, remote_root, ssh_options, observation_id, image_path)

        if not remote_path:
            results.append(
                SyncResult(
                    observation_id=observation_id,
                    source_label=source_label,
                    label=label,
                    image_path=image_path,
                    remote_path=None,
                    local_path=None,
                    status="missing_remote",
                    note=f"label_mapping={label_mapping}",
                )
            )
            continue

        label_dir = raw_dir / sanitize_filename_component(label)
        local_name = f"obs_{observation_id}_{Path(remote_path).name}"
        local_path = label_dir / sanitize_filename_component(local_name)
        copy_ok = copy_remote_file(host, scp_options, remote_path, local_path)

        results.append(
            SyncResult(
                observation_id=observation_id,
                source_label=source_label,
                label=label,
                image_path=image_path,
                remote_path=remote_path,
                local_path=str(local_path.resolve()) if copy_ok else None,
                status="copied" if copy_ok else "copy_failed",
                note=f"label_mapping={label_mapping}",
            )
        )
        if copy_ok:
            copied += 1

    report = {
        "raw_dir": str(raw_dir.resolve()),
        "copied_images": copied,
        "total_rows": len(results),
        "status_counts": {
            status: sum(1 for result in results if result.status == status)
            for status in sorted({result.status for result in results})
        },
        "class_counts": {
            label: sum(1 for result in results if result.status == "copied" and result.label == label)
            for label in sorted({result.label for result in results if result.status == "copied"})
        },
        "results": [asdict(result) for result in results],
    }
    args.report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Copied images: {copied}/{len(results)}")
    print(f"Raw dir: {raw_dir.resolve()}")
    print(f"Report: {args.report_path.resolve()}")
    for label, count in report["class_counts"].items():
        print(f" - {label}: {count}")


if __name__ == "__main__":
    main()
