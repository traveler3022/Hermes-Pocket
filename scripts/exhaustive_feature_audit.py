#!/usr/bin/env python3
"""Build a source-derived implementation inventory and validation scenario matrix.

This is an inventory aid, not a test runner. Every scenario starts as NOT RUN.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree

SOURCE_ROOT = Path("app/src/main/java/com/hermes/android")
TEST_ROOTS = (Path("app/src/test"), Path("app/src/androidTest"))
SCENARIOS = [
    ("S01", "normal_path", "Valid input through the real entry point; verify observable result and persistence/remote effect."),
    ("S02", "boundary_and_invalid_input", "Empty, min/max, long, malformed, duplicate, and unexpected values where meaningful."),
    ("S03", "missing_configuration_or_dependency", "Missing/invalid configuration, unavailable dependency, absent runtime/service, and recovery."),
    ("S04", "permission_and_os_state", "Permission grant/deny/revoke, background limits, notifications, files, voice, and Android OS state."),
    ("S05", "loading_empty_error_timeout", "Loading, empty, success, error, cancellation, timeout, and stale responses."),
    ("S06", "network_disconnect_reconnect", "Slow/offline network, disconnect mid-operation, reconnect, server restart, and stale events."),
    ("S07", "rapid_repeat_and_concurrency", "Double taps, repeated requests, concurrency, cancellation races, ordering, and idempotency."),
    ("S08", "lifecycle_and_process_recreation", "Background/foreground, navigation away/back, activity recreation, process death, and relaunch."),
    ("S09", "persistence_and_restore", "State across navigation/restart; restore sessions, settings, tasks, and local/remote reconciliation."),
    ("S10", "duration_and_resource_pressure", "Repeated/long runs, memory/CPU/latency measurements, leaks, ANRs, starvation, and recovery."),
    ("S11", "cross_feature_interactions", "Interactions, conflicting operations, stale state, and unintended side effects across dependent features."),
    ("S12", "security_and_sensitive_data", "Authorization, secret redaction, file boundaries, approval policies, and unauthorized access."),
]
NETWORK_HINTS = ("gateway", "remote", "network", "provider", "platform", "messaging", "telegram", "pairing", "dashboard", "release", "download", "session", "group", "cron", "project", "kanban", "task", "update", "mcp", "skill", "toolset")
OS_HINTS = ("screen", "viewmodel", "runtime", "service", "receiver", "assistant", "file", "storage", "audio", "media", "notification", "permission", "desktop")
PERSIST_HINTS = ("data/", "domain/", "session", "config", "provider", "profile", "project", "group", "kanban", "task", "storage", "memory", "learning", "cron", "settings")
SECURITY_HINTS = ("gateway", "remote", "auth", "token", "file", "storage", "approval", "pairing", "platform", "provider", "skill", "tool", "mcp", "secret", "redact")
STRESS_HINTS = ("gateway", "runtime", "service", "viewmodel", "session", "task", "cron", "group", "stream", "download", "storage", "network", "client")

def classify(path: str) -> str:
    p = path.lower()
    for needle, label in (
        ("/ui/screen/", "UI screen / user workflow"),
        ("/ui/viewmodel/", "UI state / ViewModel"),
        ("/ui/component/", "UI component / rendering"),
        ("/ui/i18n/", "Localization"),
        ("/gateway/", "Gateway / protocol"),
        ("/runtime/", "Runtime / platform integration"),
        ("/data/", "Data / repository"),
        ("/domain/", "Domain behavior / contract"),
        ("/service/", "Android service / lifecycle"),
        ("/assistant/", "Android assistant integration"),
        ("/diagnostics/", "Diagnostics / logging"),
        ("/di/", "Dependency injection / integration wiring"),
    ):
        if needle in p:
            return label
    return "Application core / other"

def applicability(code: str, path: str, stem: str) -> tuple[str, str]:
    value = (path + " " + stem).lower()
    if code == "S01":
        return "LIKELY_APPLICABLE", "Every behavior should have an observable contract; the test entry point may be higher-level."
    if code == "S02":
        return "REVIEW_REQUIRED", "Review public inputs and boundaries; some pure types need a smaller case set."
    if code == "S03":
        return ("LIKELY_APPLICABLE" if any(h in value for h in NETWORK_HINTS + ("runtime", "setup", "config", "install")) else "REVIEW_REQUIRED",
                "Path-name heuristic only; inspect actual dependencies.")
    if code == "S04":
        return ("LIKELY_APPLICABLE" if any(h in value for h in OS_HINTS) else "LIKELY_NOT_APPLICABLE_REVIEW",
                "Confirm OS permission/lifecycle dependencies before deciding NOT APPLICABLE.")
    if code == "S05":
        return ("LIKELY_APPLICABLE" if any(h in value for h in ("screen", "viewmodel", "data/", "gateway", "runtime", "service", "repository", "provider", "session", "task")) else "REVIEW_REQUIRED",
                "Validate stateful/loading behavior at the observable boundary.")
    if code == "S06":
        return ("LIKELY_APPLICABLE" if any(h in value for h in NETWORK_HINTS + ("runtime", "gateway", "client", "socket", "url")) else "LIKELY_NOT_APPLICABLE_REVIEW",
                "Check transitive network dependencies; source paths cannot prove absence.")
    if code == "S07":
        return ("LIKELY_APPLICABLE" if any(h in value for h in ("viewmodel", "gateway", "runtime", "repository", "session", "task", "group", "cron", "state", "stream", "service", "client")) else "REVIEW_REQUIRED",
                "Concurrency may belong to a dependency rather than this source file.")
    if code == "S08":
        return ("LIKELY_APPLICABLE" if any(h in value for h in OS_HINTS + ("session", "stream", "task")) else "LIKELY_NOT_APPLICABLE_REVIEW",
                "Determine whether lifecycle/state is observable through this unit.")
    if code == "S09":
        return ("LIKELY_APPLICABLE" if any(h in value for h in PERSIST_HINTS) else "LIKELY_NOT_APPLICABLE_REVIEW",
                "Review local/remote persistence dependencies; inferred N/A needs human confirmation.")
    if code == "S10":
        return ("LIKELY_APPLICABLE" if any(h in value for h in STRESS_HINTS) else "REVIEW_REQUIRED",
                "Use bounded workloads and repeated measurements; one snapshot cannot establish stability.")
    if code == "S11":
        return "REVIEW_REQUIRED", "Identify collaborators from navigation, call sites, dependency injection, and contracts."
    if code == "S12":
        return ("LIKELY_APPLICABLE" if any(h in value for h in SECURITY_HINTS) else "REVIEW_REQUIRED",
                "Confirm through data flows; sensitive values may cross source areas.")
    return "REVIEW_REQUIRED", "No applicability rule."

def junit_summary(root: Path) -> dict:
    candidates = []
    for base in (root / "app/build/test-results", root / "app/build/outputs/androidTest-results/connected"):
        if base.exists():
            candidates.extend(base.rglob("*.xml"))
    totals = Counter()
    files = []
    for file in sorted(set(candidates)):
        try:
            parsed = ElementTree.parse(file).getroot()
        except (ElementTree.ParseError, OSError):
            continue
        suites = [parsed] if parsed.tag == "testsuite" else list(parsed.iter("testsuite"))
        if not suites:
            continue
        local = Counter()
        for suite in suites:
            for key in ("tests", "failures", "errors", "skipped", "time"):
                try:
                    local[key] += float(suite.attrib.get(key, "0")) if key == "time" else int(suite.attrib.get(key, "0"))
                except ValueError:
                    pass
        totals.update({k: v for k, v in local.items() if k != "time"})
        totals["time_seconds"] += local["time"]
        files.append({"path": file.relative_to(root).as_posix(), **dict(local)})
    return {
        "files_found": len(files), "reported_tests": totals["tests"],
        "failures": totals["failures"], "errors": totals["errors"],
        "skipped": totals["skipped"], "time_seconds": round(totals["time_seconds"], 2),
        "junit_files": files,
    }

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", default="build/exhaustive-validation")
    parser.add_argument("--include-test-results", action="store_true")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    output = (root / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    source_base = root / SOURCE_ROOT
    if not source_base.exists():
        print(f"ERROR: source root not found: {source_base}")
        return 2

    tests = {}
    for test_root in TEST_ROOTS:
        folder = root / test_root
        if folder.exists():
            for file in sorted(folder.rglob("*.kt")):
                try:
                    tests[file.relative_to(root).as_posix()] = file.read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
    sources = sorted(
        (p for p in source_base.rglob("*.kt") if "/ui/icons/" not in p.as_posix()),
        key=lambda p: p.relative_to(root).as_posix(),
    )
    features, rows, areas = [], [], Counter()
    for source in sources:
        path = source.relative_to(root).as_posix()
        stem = source.stem
        category = classify(path)
        fid = "F-" + hashlib.sha256(path.encode()).hexdigest()[:10].upper()
        candidate_tests = [name for name, content in tests.items()
                           if re.search(r"\b" + re.escape(stem) + r"\b", content)]
        areas[category] += 1
        features.append({
            "feature_id": fid, "source_symbol_candidate": stem, "category": category,
            "source_path": path, "candidate_test_references": candidate_tests,
            "candidate_test_count": len(candidate_tests), "execution_status": "NOT RUN",
            "status_note": "Name reference is a review lead, not proof of behavioral coverage.",
        })
        for code, label, intent in SCENARIOS:
            review, reason = applicability(code, path, stem)
            rows.append({
                "feature_id": fid, "feature_candidate": stem, "category": category,
                "source_path": path, "candidate_test_references": ";".join(candidate_tests),
                "scenario_id": code, "scenario": label, "scenario_intent": intent,
                "applicability_review": review, "applicability_rationale": reason,
                "status": "NOT RUN", "evidence": "", "defect_or_blocker": "",
            })

    (output / "feature-inventory.json").write_text(json.dumps({
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source_root": SOURCE_ROOT.as_posix(), "source_file_count": len(sources),
        "test_source_file_count": len(tests), "feature_candidates": features,
        "note": "Static source-derived inventory. Candidate test references are heuristic. Scenario rows remain NOT RUN until supported by execution evidence.",
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    columns = list(rows[0].keys()) if rows else [
        "feature_id", "feature_candidate", "category", "source_path", "candidate_test_references",
        "scenario_id", "scenario", "scenario_intent", "applicability_review",
        "applicability_rationale", "status", "evidence", "defect_or_blocker",
    ]
    with (output / "feature-scenario-matrix.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)

    junit = junit_summary(root) if args.include_test_results else None
    linked = sum(bool(feature["candidate_test_references"]) for feature in features)
    report = [
        "# Exhaustive Feature Validation — Source Audit", "",
        f"- Generated (UTC): {datetime.now(timezone.utc).isoformat()}",
        f"- Source-derived implementation candidates: **{len(sources)}**",
        f"- Kotlin test files discovered: **{len(tests)}**",
        f"- Candidates with at least one exact symbol reference in tests: **{linked}**",
        f"- Scenario matrix rows: **{len(rows)}** ({len(SCENARIOS)} dimensions per source unit)", "",
        "## Interpretation", "",
        "This is a source-path inventory, not a guarantee that every business-level feature has been identified. Exact-name matches are leads for review only and do not prove assertion quality, real-device execution, or end-to-end coverage.",
        "Every scenario row starts as NOT RUN. JUnit/device artifacts provide run evidence. Do not mark PASS without a verifiable test assertion and result. Confirm likely-N/A decisions manually and record a reason before using NOT APPLICABLE.", "",
        "## Source areas", "", "| Area | Kotlin files |", "|---|---:|",
    ]
    report += [f"| {area} | {count} |" for area, count in sorted(areas.items())]
    report += ["", "## Scenario dimensions", "", "| ID | Scenario |", "|---|---|"]
    report += [f"| {code} | {label} |" for code, label, _ in SCENARIOS]
    if junit is not None:
        report += [
            "", "## JUnit results found at report-generation time", "",
            f"- JUnit XML files parsed: **{junit['files_found']}**",
            f"- Reported tests: **{junit['reported_tests']}**",
            f"- Failures: **{junit['failures']}**", f"- Errors: **{junit['errors']}**",
            f"- Skipped: **{junit['skipped']}**", f"- Reported duration: **{junit['time_seconds']} seconds**",
            "", "Skipped tests are not passes; live-test assumptions may be reported as skipped.",
        ]
        (output / "junit-summary.json").write_text(json.dumps(junit, indent=2) + "\n", encoding="utf-8")
    report += [
        "", "## Artifacts", "",
        "- feature-inventory.json: implementation candidates and candidate test references.",
        "- feature-scenario-matrix.csv: one row per source candidate and scenario dimension.",
        "- audit-summary.md: this report.", "", "## Limits", "",
        "- Reconcile source units with README/release notes, navigation, permissions, API contracts, and dependency injection.",
        "- A launch smoke check and a single memory snapshot do not certify performance, leaks, stress limits, or long-run stability.",
        "- Optional live Gateway tests should target a dedicated staging environment and may consume model quota.",
        "- The matrix is a work queue; status is never inferred from filenames or symbol matches.", "",
    ]
    (output / "audit-summary.md").write_text("\n".join(report), encoding="utf-8")
    print(f"Feature audit wrote {len(sources)} implementation candidates and {len(rows)} scenario rows.")
    if junit is not None:
        print(f"JUnit: {junit['reported_tests']} tests; {junit['failures']} failures; {junit['errors']} errors; {junit['skipped']} skipped.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
