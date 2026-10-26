"""Check fixture integrity and independent numeric anchors using Python stdlib.

This does not run the candidate framework, render a UI, or certify any host.
Run reference-core/runner.clj --check for actual Clojure core execution.
"""
import datetime as dt
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def strict_json(text):
    def reject_constant(value):
        raise ValueError(f"Non-finite JSON constant: {value}")

    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON object key: {key}")
            result[key] = value
        return result

    def finite_float(text):
        value = float(text)
        if not math.isfinite(value):
            raise ValueError("JSON number exceeds finite floating point range")
        return value

    return json.loads(text, parse_constant=reject_constant,
                      object_pairs_hook=unique_object, parse_float=finite_float)


def read(name):
    return strict_json((ROOT / name).read_text(encoding="utf-8"))


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def close(actual, expected):
    require(isinstance(actual, (int, float)) and not isinstance(actual, bool)
            and math.isclose(actual, expected, rel_tol=1e-9, abs_tol=1e-9),
            f"Numeric anchor mismatch: {actual!r} != {expected!r}")


def compare(actual, expected, path):
    if expected is None or isinstance(expected, bool):
        require(actual is expected, f"{path}: expected {expected!r}, got {actual!r}")
    elif isinstance(expected, int):
        require(type(actual) is int and actual == expected,
                f"{path}: exact integer {expected!r} required, got {actual!r}")
    elif isinstance(expected, float):
        require(type(actual) in (int, float) and math.isfinite(actual)
                and math.isclose(actual, expected, rel_tol=1e-9, abs_tol=1e-9),
                f"{path}: expected numeric {expected!r}, got {actual!r}")
    elif isinstance(expected, str):
        require(type(actual) is str and actual == expected, f"{path}: string mismatch")
    elif isinstance(expected, list):
        require(type(actual) is list and len(actual) == len(expected), f"{path}: array length/type mismatch")
        for i, (a, e) in enumerate(zip(actual, expected)):
            compare(a, e, f"{path}[{i}]")
    elif isinstance(expected, dict):
        require(type(actual) is dict and set(actual) == set(expected), f"{path}: object keys/type mismatch")
        for key, value in expected.items():
            compare(actual[key], value, f"{path}.{key}")
    else:
        raise AssertionError(f"{path}: unsupported oracle type")


def check_observed(path, frozen):
    observed = strict_json(Path(path).read_text(encoding="utf-8"))
    require(type(observed) is dict and set(observed) == {"schema_version", "host", "candidate_revision", "cases"}, "Observation envelope fields")
    require(observed["schema_version"] == "FITNESS_BUSINESS_RESULTS_V1", "Observation schema")
    require(observed["host"] in ("jvm", "dart", "clr"), "Host must be jvm, dart, or clr")
    require(type(observed["candidate_revision"]) is str and observed["candidate_revision"].strip(), "Candidate revision required")
    require(type(observed["cases"]) is list, "Observed cases must be an array")
    wanted = {case["id"]: case["expected"] for case in frozen["cases"]}
    actual = {}
    for case in observed["cases"]:
        require(type(case) is dict and set(case) == {"id", "actual"}, "Observed case fields")
        require(type(case["id"]) is str and case["id"] not in actual, "Duplicate or invalid observation ID")
        actual[case["id"]] = case["actual"]
    require(set(actual) == set(wanted), "Observation IDs must contain all 37 cases exactly once, without extras")
    for key, expected in wanted.items():
        compare(actual[key], expected, key)
    print(f"PASS: all 37 submitted business results match for host={observed['host']} revision={observed['candidate_revision']}.")
    print("This compares submitted outputs; capture logs and host execution evidence separately.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observed", help="Candidate observation JSON to compare with every frozen expected result")
    args = parser.parse_args()
    source = read("fixtures/business-inputs.json")
    frozen = read("fixtures/business-cases.json")
    require(source["schema_version"] == frozen["schema_version"] == "FITNESS_BUSINESS_CASES_V1", "Schema")
    require(source["synthetic"] is True and frozen["synthetic"] is True, "Synthetic marker")
    require(len(source["cases"]) == len(frozen["cases"]) == 37, "Case count")
    ids = [case["id"] for case in frozen["cases"]]
    require(len(set(ids)) == len(ids), "Duplicate case ID")
    for before, after in zip(source["cases"], frozen["cases"]):
        require(before == {k: v for k, v in after.items() if k != "expected"}, "Input drift")
        require(set(after) == {"id", "operation", "input", "expected"}, "Case fields")
    out = {case["id"]: case["expected"] for case in frozen["cases"]}
    inp = {case["id"]: case["input"] for case in frozen["cases"]}
    require(out["CAT-01"] == ["press", "curl", "pushup"], "OR inside muscle category")
    require(out["CAT-02"] == ["press"], "AND across filter categories")
    require(out["CAT-03"] == ["press", "pushup"], "Secondary muscle membership")
    require(out["CAT-04"] == ["mobility"], "Unset is not No equipment")
    require(out["CAT-05"] == ["press"], "Case-insensitive trimmed search")
    require(out["CAT-06"] == ["press", "curl", "pushup", "mobility"], "Clear filters/source order")
    require(out["CAT-07"] == ["Resistance band", "Chair"], "Equipment normalization")
    require(out["CAT-08"] == ["No equipment"], "Equipment exclusivity")
    require([e["intensity"] for e in out["CAT-09"]] == ["low", "low", "medium", "medium", "high", "low"], "Intensity boundaries")
    require([i["id"] for i in out["PLAN-01"]["items"]] == ["press-a", "press-copy", "press-b", "curl-a"], "Copy identity/order")
    require(out["PLAN-02"] == {"reason": "duplicate-id"}, "Colliding plan item ID")
    close(out["REC-01"]["duration-sec"], 90)
    base = inp["REC-02"]["record"]["started-at"]
    require(out["REC-02"]["id"] == "record-press-a" and out["REC-02"]["date"] == "2026-10-13", "Snapshot identity")
    require(out["REC-02"]["segments"] == [[base, base + 30000], [base + 90000, base + 120000]], "Finish closes only active spans")
    require(out["REC-03"]["unchanged?"] is True, "Idempotent completion")
    require(out["REC-04"] == [[base, base + 70000], [base + 90000, base + 120000]], "Merge overlapping intervals")
    require(out["MUS-01"] == {"pectoralis-major": 1.0, "triceps-brachii": 0.5}, "Deduplicate muscles/primary wins")
    close(out["HR-01"]["active-ms"], 90000)
    close(out["HR-01"]["covered-ms"], 90000)
    close(out["HR-01"]["average-bpm"], (100 * 60000 + 120 * 30000) / 90000)
    close(out["HR-02"]["coverage"], 1 / 9)
    close(out["HR-02"]["average-bpm"], 110)
    require(out["HR-03"]["duplicates"] == 1 and out["HR-03"]["samples"][1]["bpm"] == 110, "Last valid HR duplicate")
    require([e["reason"] for e in out["HR-03"]["errors"]] == ["invalid-bpm", "invalid-timestamp", "invalid-end"], "Invalid sample reasons")
    close(out["CAL-01"]["kcal"], 4 * 3.5 * 70 / 200 * 1.5)
    require(out["CAL-02"]["kcal"] is None and out["CAL-02"]["reason"] == "missing-weight", "Do not invent weight")
    require(out["CAL-03"]["reason"] == "zero-duration", "Zero active duration")
    require(out["CAL-04"]["reason"] == "incomplete", "Incomplete record")
    close(out["CAL-03"]["kcal"], 0)
    close(out["CAL-04"]["kcal"], 0)
    close(out["CAL-05"]["hr-coverage"], 0.5)
    close(out["CAL-05"]["kcal"], 4.9 * (1 + 0.5 * (1.2 - 1)))
    require(out["CAL-06"]["unchanged?"] is True and out["CAL-06"]["before"] == out["CAL-06"]["after"], "Target HR is not measured HR")
    close(out["CAL-07"]["frequency-factor"], 1)
    require(out["CAL-07"]["method"] == "met", "Any matched HR suppresses frequency heuristic")
    close(out["CAL-08"]["2026-10-13"]["duration-min"], 2.5)
    close(out["CAL-08"]["2026-10-13"]["kcal"], 12.25)
    require(out["CAL-08"]["2026-10-13"]["record-count"] == 2, "Overlap/duplicate record accounting")
    require(out["AN-01"]["current"]["sets"] == 2 and out["AN-01"]["previous"]["sets"] == 1, "Actual group comparison")
    close(out["AN-01"]["comparison"]["sets"]["percent"], 100)
    require(out["AN-02"]["sets"] == 3, "Repeated planned exercises remain distinct")
    require(out["AN-03"]["sets"] == 1 and out["AN-04"]["sets"] == 0, "Split group earliest-date ownership")
    close(out["AN-03"]["duration-min"], 3)
    close(out["AN-04"]["duration-min"], 2)
    require(out["AN-04"]["muscles"] == {}, "No second group on next day")
    require(out["AN-05"]["muscles"] == {"pectoralis-major": 2.0, "triceps-brachii": 1.0}, "Minutes still follow each daily slice")
    require(out["AN-06"] == {"from": "2024-02-28", "to": "2024-02-29"}, "Leap-day prior range")
    require(out["AN-07"] == {"from": "2026-03-01", "to": "2026-03-07"}, "Calendar-day range, not epoch/DST arithmetic")
    require(out["AN-08"]["comparison"]["sets"]["percent"] is None, "No percentage from zero denominator")
    for key in ("AN-09", "AN-10"):
        cells = out[key]["cells"]
        require(len(cells) == 42, "42-cell month")
        dates = [dt.date.fromisoformat(cell["date"]) for cell in cells]
        require(dates[0].weekday() == 0, "Monday-first month")
        require(all((b - a).days == 1 for a, b in zip(dates, dates[1:])), "Continuous calendar dates")
    require(sum(c["in-month?"] for c in out["AN-10"]["cells"]) == 29, "Leap February")
    day = next(c for c in out["AN-09"]["cells"] if c["date"] == "2026-10-13")
    require(day["planned-items"] == 3 and day["completed-items"] == 2 and not day["plan-complete?"], "Calendar plan/item completion")

    doc = read("fixtures/demo-document.json")
    muscles = {m["id"] for m in read("fixtures/muscle-dictionary.json")}
    require(len(muscles) == 20, "Twenty muscle identifiers")
    exercises = {e["id"] for e in doc["exercises"]}
    require(len(exercises) == len(doc["exercises"]), "Unique exercise IDs")
    for exercise in doc["exercises"]:
        require(set(exercise["primary"] + exercise["secondary"]) <= muscles, "Known muscle IDs")
        require(not set(exercise["primary"]) & set(exercise["secondary"]), "Disjoint main/secondary selection in UI fixture")
    for plan in doc["plans"]:
        require(len({i["id"] for i in plan["items"]}) == len(plan["items"]), "Unique plan item IDs")
        require(all(i["exercise-id"] in exercises for i in plan["items"]), "Resolvable planned exercises")
    require(len({r["id"] for r in doc["records"]}) == len(doc["records"]), "Unique fixture records")
    require(doc == inp["AN-01"]["document"], "UI fixture agrees with analytics oracle input")
    print("PASS: 37 fixture cases, independent numeric anchors, and synthetic document integrity.")
    print("Scope: package consistency only; no candidate framework or GUI host was executed.")
    if args.observed:
        check_observed(args.observed, frozen)


if __name__ == "__main__":
    main()
