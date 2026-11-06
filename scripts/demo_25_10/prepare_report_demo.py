"""Reconcile the closed synthetic Report demo fixture through the real ADMIN API.

This is confined to the active disposable rehearsal. A normal invocation only previews and
writes five clearly synthetic source exports. Coverage changes require the explicit --accept
flag, which is what the default local demo launcher passes for its known demo fixture.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import sys
import time
import urllib.parse
import uuid

import report_demo_seed as seed_data
from prepare_ui_demo import ApiError, SEED_LOGIN_EMAILS

HERE = Path(__file__).resolve().parent
ZONE = dt.timezone(dt.timedelta(hours=7))
ADMIN_USERNAME = SEED_LOGIN_EMAILS["admin"]
DOCTOR_USERNAME = SEED_LOGIN_EMAILS["doctor"]
ACCEPTANCE_REQUEST_ID = uuid.uuid5(seed_data.NAMESPACE, "acceptance:2024:general-clinic")
ACCEPTANCE_REFERENCE = "SYNTHETIC-DEMO-REPORT-2024:" + seed_data.DEPARTMENT_IDS[0]
DAILY_SAMPLE_DATE = dt.date(2024, 6, 17)
PREVIEW_RETRY_ATTEMPTS = 16
PREVIEW_RETRY_DELAY_SECONDS = 2


def validate_manifest(manifest):
    gateway = manifest.get("gateway", "")
    parsed = urllib.parse.urlsplit(gateway)
    evidence = Path(manifest["evidence"]).resolve()
    evidence_root = (HERE / "evidence").resolve()
    ports = manifest.get("ports") or {}
    gateway_port = ports.get("gateway")
    if (parsed.scheme != "http" or parsed.hostname != "127.0.0.1" or not isinstance(gateway_port, int)
            or parsed.port != gateway_port
            or parsed.path or parsed.query or parsed.fragment or parsed.username
            or not manifest.get("scheduling") or not evidence.is_relative_to(evidence_root)
            or Path(manifest["stopFile"]).resolve() != evidence / "stop"
            or Path(manifest["stopFile"]).exists()):
        raise ValueError("Use the active disposable scheduling rehearsal only")
    return gateway, evidence


def _export_bytes(document):
    return (json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def _available(api):
    query = urllib.parse.urlencode({"departmentId": seed_data.DEPARTMENT_IDS[0]})
    return api.call("admin", "GET", "/api/v1/reports/outpatient/available-periods?" + query)


def appoint_department_head(api):
    """Appoint the seeded doctor head of their own department so they get the fixed-department report."""
    api.login("doctor", DOCTOR_USERNAME)
    doctor_id = api.claims["doctor"]["staffId"]
    department_id = api.call("admin", "GET", "/api/v1/org/staff/" + doctor_id)["departmentId"]
    path = "/api/v2/org/departments/" + department_id + "/head-appointments"
    current = api.call("admin", "GET", path)
    if current.get("staffId") == doctor_id:
        return current
    if current.get("staffId"):
        raise RuntimeError("Department already has a different head; do not replace it automatically")
    return api.call("admin", "PUT", path,
                    {"expectedRevision": current["revision"], "staffId": doctor_id,
                     "reason": "Bo nhiem truong khoa cho demo bao cao"},
                    stage="report-demo-department-head")


def _coverage_preview(api, path):
    """Wait briefly for Gateway's Eureka cache to discover Support after stack startup."""
    for attempt in range(PREVIEW_RETRY_ATTEMPTS):
        try:
            return api.call("admin", "GET", path)
        except ApiError as error:
            is_gateway_discovery_race = (
                error.status == 503 and "GATEWAY_UPSTREAM_UNAVAILABLE" in str(error))
            if not is_gateway_discovery_race or attempt + 1 == PREVIEW_RETRY_ATTEMPTS:
                raise
            time.sleep(PREVIEW_RETRY_DELAY_SECONDS)


def _is_available_annual_2024(periods):
    return any(period.get("fromDate") == "2024-01-01" and period.get("toDate") == "2024-12-31"
               and period.get("departmentId") == seed_data.DEPARTMENT_IDS[0]
               and period.get("syntheticData") is True for period in periods)


def prepare(api, evidence, accept=False, now=None):
    """Write evidence, compare every fixture count with API preview, then optionally accept."""
    now = now or dt.datetime.now(dt.timezone.utc)
    if now.tzinfo is None:
        raise ValueError("A timezone-aware observation time is required")
    evidence = Path(evidence)
    exports_dir = evidence / "report-source-exports" / "2024-annual"
    exports_dir.mkdir(parents=True, exist_ok=True)
    fixture = seed_data.build_seed()
    source_proofs = {}
    fixture_counts = {}
    for source in seed_data.EXPORT_SOURCES:
        document = seed_data.export_document(fixture, source)
        records = document["records"]
        payload = _export_bytes(document)
        output = exports_dir / (source + ".json")
        output.write_bytes(payload)
        digest = hashlib.sha256(payload).hexdigest()
        fixture_counts[source] = len(records)
        source_proofs[source] = {"reference": "SYNTHETIC-DEMO-REPORT-2024/" + source + ".json",
                                 "exportSha256": digest, "rowCount": len(records)}

    api.login("admin", ADMIN_USERNAME)
    appoint_department_head(api)
    scope = urllib.parse.urlencode({"fromDate": seed_data.HISTORICAL_FROM.isoformat(),
                                    "toDate": seed_data.HISTORICAL_TO.isoformat(),
                                    "departmentId": seed_data.DEPARTMENT_IDS[0]})
    preview = _coverage_preview(api, "/api/v1/reports/outpatient/coverage/preview?" + scope)
    if preview.get("pendingOrInvalid"):
        raise RuntimeError("Report preview has pending/invalid local evidence; synthetic coverage was not accepted")
    if not preview.get("fingerprint"):
        raise RuntimeError("Report preview omitted its local evidence fingerprint")
    for source in seed_data.EXPORT_SOURCES:
        local = preview.get("sources", {}).get(source, {})
        if (local.get("rowCount") != fixture_counts[source] or not local.get("fingerprint")):
            raise RuntimeError(f"Synthetic {source} export does not match Report preview counts; inspect {exports_dir}")
        source_proofs[source]["localFingerprint"] = local["fingerprint"]

    periods_before = _available(api)
    acceptance = None
    if not _is_available_annual_2024(periods_before) and accept:
        observed = now.astimezone(dt.timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")
        request = {
            "requestId": str(ACCEPTANCE_REQUEST_ID),
            "fromDate": seed_data.HISTORICAL_FROM.isoformat(),
            "toDate": seed_data.HISTORICAL_TO.isoformat(),
            "departmentId": seed_data.DEPARTMENT_IDS[0],
            "zoneId": seed_data.REPORT_ZONE,
            "currency": seed_data.CURRENCY,
            "sources": source_proofs,
            "sourceObservedAt": observed,
            "reconciledAt": observed,
            "acceptanceReference": ACCEPTANCE_REFERENCE,
            "expectedFingerprint": preview["fingerprint"],
        }
        acceptance = api.call("admin", "POST", "/api/v1/reports/outpatient/coverage/acceptances",
                              request, stage="report-demo-2024-coverage",
                              journal_omit=("sources", "expectedFingerprint", "sourceObservedAt", "reconciledAt"))

    periods = _available(api)
    accepted = _is_available_annual_2024(periods)
    daily = monthly = None
    if accepted:
        daily = api.call("admin", "GET", "/api/v1/reports/outpatient/daily?" + urllib.parse.urlencode({
            "fromDate": DAILY_SAMPLE_DATE.isoformat(), "toDate": DAILY_SAMPLE_DATE.isoformat(),
            "departmentId": seed_data.DEPARTMENT_IDS[0]}))
        monthly = api.call("admin", "GET", "/api/v1/reports/outpatient/monthly?" + urllib.parse.urlencode({
            "year": "2024", "departmentId": seed_data.DEPARTMENT_IDS[0]}))
        expected_visits = sum(visit.department_id == seed_data.DEPARTMENT_IDS[0]
                              and visit.business_date == DAILY_SAMPLE_DATE for visit in fixture.visits)
        daily_period = daily.get("periods", [{}])[0]
        if not daily.get("covered") or daily_period.get("completedVisits") != expected_visits:
            raise RuntimeError("Historical daily API read did not match the synthetic fixture")
        if not monthly.get("covered") or len(monthly.get("periods", [])) != 12:
            raise RuntimeError("Historical monthly API read did not return all twelve periods")

    stats_scope = urllib.parse.urlencode({"fromDate": seed_data.CURRENT_FROM.isoformat(),
                                          "toDate": seed_data.CURRENT_TO.isoformat()})
    current_statistics = api.call("admin", "GET", "/api/v1/reports/outpatient/statistics?" + stats_scope)
    current_visits = sum(visit.business_date.year == seed_data.CURRENT_TO.year for visit in fixture.visits)
    current_totals = current_statistics.get("totals", {})
    if (current_statistics.get("reconciled") is not False
            or current_totals.get("completedVisits", 0) < current_visits
            or len(current_statistics.get("departments", [])) < len(seed_data.DEPARTMENT_IDS)
            or not current_statistics.get("days")):
        raise RuntimeError("Current demo statistics did not show the provisional multi-department fixture")

    manifest = {
        "provenance": "SYNTHETIC-DISPOSABLE-REPORT-DEMO-ONLY",
        "scope": {"fromDate": "2024-01-01", "toDate": "2024-12-31",
                  "departmentId": seed_data.DEPARTMENT_IDS[0], "zoneId": seed_data.REPORT_ZONE, "currency": "VND"},
        "sources": source_proofs,
        "previewFingerprint": preview["fingerprint"],
        "accepted": accepted,
        "acceptanceAddedThisRun": acceptance is not None,
        "acceptedCoverage": acceptance,
        "availableSyntheticPeriods": sum(1 for period in periods if period.get("syntheticData") is True),
        "availablePeriods": periods,
        "historicalDailyRead": daily,
        "historicalMonthlyRead": monthly,
        "currentStatistics": current_statistics,
    }
    (evidence / "report-demo-acceptance.local.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway", default=None, help="Must match the active local disposable rehearsal.")
    parser.add_argument("--accept", action="store_true",
                        help="Submit the explicit ADMIN acceptance for the reviewed synthetic fixture.")
    args = parser.parse_args(argv)
    manifest_path = HERE / "evidence" / "active-rehearsal.local.json"
    if not manifest_path.exists():
        parser.error("No active disposable rehearsal manifest; start rehearse_snapshot.py first")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    try:
        gateway, evidence = validate_manifest(manifest)
        if args.gateway and args.gateway.rstrip("/") != gateway:
            raise ValueError("Gateway does not match the active disposable rehearsal")
        from prepare_ui_demo import DemoApi
        result = prepare(DemoApi(gateway, evidence / "report-demo-journal.local.json",
                                 evidence / "stop"), evidence, accept=args.accept)
    except (OSError, ValueError, RuntimeError) as error:
        parser.error(str(error))
    print(json.dumps({"accepted": result["accepted"],
                      "acceptanceAddedThisRun": result["acceptanceAddedThisRun"],
                      "availableSyntheticPeriods": result["availableSyntheticPeriods"],
                      "evidence": str(evidence / "report-demo-acceptance.local.json")}, indent=2))


if __name__ == "__main__":
    main()
