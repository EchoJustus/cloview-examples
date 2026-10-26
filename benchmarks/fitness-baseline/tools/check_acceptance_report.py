"""Validate future candidate reports and optionally enforce selected-profile gates.

Evidence presence is checked; evidence truth and actual execution need review.
No host success is inferred from a syntactically valid report.
"""
import argparse
from collections import Counter
import json
import math
from pathlib import Path, PurePosixPath
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
STATUSES = {"PASS", "FAIL", "NOT_RUN", "BLOCKED", "UNSUPPORTED"}
PROFILES = {"CORE", "FITNESS_NATIVE", "OPTIONAL_INTEGRATIONS"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def nonblank(value):
    return isinstance(value, str) and bool(value.strip())


def portable(value):
    return (nonblank(value) and not PurePosixPath(value).is_absolute()
            and "\\" not in value and ":" not in value
            and all(p not in ("", ".", "..") for p in value.split("/")))


def load(path):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "Duplicate JSON field: " + key)
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=pairs,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Non-finite JSON number")))


def validate(report, cases, directory, gate=False, full=False):
    require(report.get("schema_version") == "FITNESS_ACCEPTANCE_RESULTS_V1", "Wrong report schema")
    selected = report.get("selected_profiles")
    require(isinstance(selected, list) and all(isinstance(p, str) for p in selected), "Invalid profiles")
    require(len(selected) == len(set(selected)) and set(selected) <= PROFILES and "CORE" in selected,
            "CORE is required; selected profiles must be unique and known")
    if full:
        require({"CORE", "FITNESS_NATIVE"} <= set(selected), "Full fitness parity requires CORE and FITNESS_NATIVE")
    catalog = {case["id"]: case for case in cases}
    require(len(catalog) == len(cases), "Duplicate case ID in case catalog")
    expected = {(host, case["id"]) for case in cases for host in case["applicable_hosts"]}
    expected_hosts = {host for host, _ in expected}
    hosts = report.get("hosts")
    require(isinstance(hosts, list) and all(isinstance(h, dict) for h in hosts), "Invalid host list")
    by_host = {host.get("id"): host for host in hosts}
    require(len(by_host) == len(hosts) and set(by_host) == expected_hosts, "Host list differs from benchmark")
    results = report.get("results")
    require(isinstance(results, list), "Results must be an array")
    observed_pairs = set()
    counters = Counter()
    blockers = []
    any_pass = False
    for item in results:
        require(isinstance(item, dict), "Each result must be an object")
        pair = (item.get("host"), item.get("case_id"))
        require(pair in expected and pair not in observed_pairs, "Unknown or duplicate host/case pair: " + str(pair))
        observed_pairs.add(pair)
        status = item.get("status")
        require(status in STATUSES, "Unknown result status")
        counters[status] += 1
        case = catalog[pair[1]]
        evidence = item.get("evidence")
        require(isinstance(evidence, list), "Evidence must be an array of relative file paths")
        require(all(portable(path) for path in evidence), "Evidence paths must be portable relative paths")
        require(len(evidence) == len(set(evidence)), "Duplicate evidence path")
        for path in evidence:
            target = directory / path
            require(target.is_file() and not target.is_symlink(), "Evidence file missing or linked: " + path)
            require(directory.resolve() in target.resolve().parents, "Evidence path leaves report directory")
        duration = item.get("duration_ms")
        require(duration is None or (type(duration) in (int, float) and math.isfinite(duration) and duration >= 0),
                "Duration must be a finite nonnegative number or null")
        if status == "PASS":
            any_pass = True
            require(evidence and nonblank(item.get("observed")), "PASS requires evidence and an observation")
            host = by_host[pair[0]]
            for key in ("os", "runtime", "toolkit", "build_command", "launch_command"):
                require(nonblank(host.get(key)), "PASS requires host metadata: " + key)
            require(isinstance(host.get("fixture_sha256"), str) and re.fullmatch(r"[0-9a-fA-F]{64}", host["fixture_sha256"]),
                    "PASS requires the tested fixture SHA-256")
        elif status in {"FAIL", "BLOCKED", "UNSUPPORTED"}:
            require(nonblank(item.get("reason")), status + " requires a reason")
        if case["profile"] in selected and case.get("mandatory", True) and status != "PASS":
            blockers.append(pair)
    require(observed_pairs == expected, "Missing host/case pairs")
    candidate = report.get("candidate")
    require(isinstance(candidate, dict), "Candidate metadata missing")
    shared = candidate.get("shared_source_manifest")
    overrides = candidate.get("host_overrides")
    require(isinstance(shared, list) and isinstance(overrides, list), "Source provenance arrays missing")
    paths = set()
    for entry in shared:
        require(isinstance(entry, dict) and portable(entry.get("path")), "Invalid shared source path")
        require(entry["path"] not in paths, "Duplicate shared source path")
        paths.add(entry["path"])
        require(isinstance(entry.get("sha256"), str) and re.fullmatch(r"[0-9a-fA-F]{64}", entry["sha256"]),
                "Invalid shared source hash")
    for entry in overrides:
        require(isinstance(entry, dict) and entry.get("host") in expected_hosts
                and portable(entry.get("path")) and nonblank(entry.get("reason")), "Invalid host override entry")
    if any_pass:
        require(shared and nonblank(candidate.get("framework_revision"))
                and nonblank(candidate.get("application_revision")), "PASS requires revisions and shared-source provenance")
    print("Report structure VALID: " + ", ".join(f"{key}={value}" for key, value in sorted(counters.items())))
    print(f"Selected-profile gate: {'INCOMPLETE' if blockers else 'PASS'} ({len(blockers)} required results not PASS).")
    print("File presence and assertions do not prove evidence authenticity; review actual execution.")
    if gate or full:
        require(not blockers, "Selected profiles have unpassed required cases")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path)
    parser.add_argument("--require-pass", action="store_true")
    parser.add_argument("--full", action="store_true", help="Require CORE plus FITNESS_NATIVE and their passes")
    args = parser.parse_args()
    report = load(args.report)
    catalog = load(ROOT / "acceptance" / "cases.json")
    validate(report, catalog["cases"], args.report.resolve().parent,
             args.require_pass, args.full)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as error:
        print("Report validation FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
