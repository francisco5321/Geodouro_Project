from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import psycopg


SCIENTIFIC_NAME_PATTERN = re.compile(r"^[A-Z][a-zA-Z-]+(?: [a-z][a-zA-Z-]+)+$")


@dataclass
class MatchResult:
    source_name: str
    normalized_name: str
    match_type: str
    matched_label: str | None
    note: str = ""


def load_config(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def build_dsn() -> str:
    return "postgresql://postgres:123@localhost:5432/Geodouro"


def normalize_name(name: str) -> str:
    return " ".join(name.strip().split())


def is_binomial(name: str) -> bool:
    return bool(SCIENTIFIC_NAME_PATTERN.match(name))


def load_training_labels(path: Path) -> list[str]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_db_candidate_names() -> list[str]:
    dsn = build_dsn()
    query = """
        WITH names AS (
            SELECT scientific_name AS source_name
            FROM plant_species
            WHERE scientific_name IS NOT NULL
            UNION
            SELECT enriched_scientific_name AS source_name
            FROM observation
            WHERE enriched_scientific_name IS NOT NULL
            UNION
            SELECT predicted_scientific_name AS source_name
            FROM observation
            WHERE predicted_scientific_name IS NOT NULL
        )
        SELECT DISTINCT source_name
        FROM names
        WHERE source_name IS NOT NULL
          AND btrim(source_name) <> ''
        ORDER BY source_name ASC
    """
    with psycopg.connect(build_dsn()) as conn:
        with conn.cursor() as cur:
            cur.execute(query)
            return [row[0] for row in cur.fetchall()]


def match_name(name: str, labels: list[str], label_set: set[str]) -> MatchResult:
    normalized = normalize_name(name)

    if normalized in label_set:
        return MatchResult(
            source_name=name,
            normalized_name=normalized,
            match_type="exact",
            matched_label=normalized,
        )

    tokens = normalized.split()
    genus = tokens[0] if tokens else ""
    genus_matches = [label for label in labels if label.startswith(genus + " ")]

    if len(tokens) == 1 and len(genus_matches) == 1:
        return MatchResult(
            source_name=name,
            normalized_name=normalized,
            match_type="genus_unique_fallback",
            matched_label=genus_matches[0],
            note="Single species found for genus in current iNat labels.",
        )

    if len(tokens) == 1 and genus_matches:
        return MatchResult(
            source_name=name,
            normalized_name=normalized,
            match_type="unresolved_genus_only",
            matched_label=None,
            note=f"{len(genus_matches)} species share this genus in current iNat labels.",
        )

    if not is_binomial(normalized):
        return MatchResult(
            source_name=name,
            normalized_name=normalized,
            match_type="invalid_scientific_name",
            matched_label=None,
            note="Name does not look like a clean binomial scientific name.",
        )

    return MatchResult(
        source_name=name,
        normalized_name=normalized,
        match_type="missing_in_inat_mini",
        matched_label=None,
        note="Valid-looking species name, but absent from current iNat 2021 train_mini labels.",
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build an allowed-labels file for focused iNaturalist training from the local Geodouro database."
    )
    parser.add_argument("--config", type=Path, default=Path("config.local.json"))
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("data/processed/focused_labels"),
    )
    args = parser.parse_args()

    config = load_config(args.config)
    splits_dir = Path(config["dataset"]["splits_dir"])
    labels_path = splits_dir / "labels.json"
    labels = load_training_labels(labels_path)
    label_set = set(labels)

    results = [match_name(name, labels, label_set) for name in load_db_candidate_names()]
    matched_labels = sorted({result.matched_label for result in results if result.matched_label})

    args.output_dir.mkdir(parents=True, exist_ok=True)
    allowed_labels_path = args.output_dir / "allowed_labels.txt"
    report_path = args.output_dir / "match_report.json"

    allowed_labels_path.write_text("\n".join(matched_labels) + ("\n" if matched_labels else ""), encoding="utf-8")
    report_path.write_text(
        json.dumps(
            {
                "allowed_labels_path": str(allowed_labels_path.resolve()),
                "matched_label_count": len(matched_labels),
                "results": [asdict(result) for result in results],
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"Allowed labels saved to: {allowed_labels_path.resolve()}")
    print(f"Match report saved to: {report_path.resolve()}")
    print(f"Matched labels: {len(matched_labels)}")
    for label in matched_labels:
        print(f" - {label}")


if __name__ == "__main__":
    main()
