"""Deterministic synthetic Report-only data for the disposable demo rehearsal.

These rows exercise Report projections and charts. They are not producer records and this
module never creates, edits or removes accepted coverage. The separate ADMIN API preparation
path may accept the closed synthetic 2024 window after comparing reviewed fixture exports.
"""
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import uuid

HERE = Path(__file__).resolve().parent
ARTIFACT_PATH = HERE / "seed_report_demo.sql"
MANIFEST_PATH = HERE / "report_demo_seed_manifest.json"
NAMESPACE = uuid.UUID("817a6817-434d-53cb-8a6d-44e4dc60154d")
REPORT_ZONE = "Asia/Bangkok"
CURRENCY = "VND"
EXPORT_SOURCES = ("CLINICAL", "LAB", "PHARMACY", "BILLING_CASH", "BILLING_REFUND")
DEPARTMENT_IDS = (
    "9a6dd24a-1c1f-4122-9e31-7cfea345ec45",  # General outpatient
    "601fc45a-d78b-4821-a309-c3bc0169fa03",  # Pediatrics
    "c68138a8-fcb3-4ec2-b10a-50c3f38076a4",  # Cardiology
    "6f1627e2-6faf-4162-9c67-9363492d187a",  # ENT
)
PATIENT_IDS = (
    "54fb1ca5-65a7-4ba2-9a0a-48c65832b246", "a834af06-1bb4-4736-a203-6c064a9e8f9a",
    "c48336b8-df92-444a-82db-1452b29dfbac", "b17af0fa-ec74-4a93-9465-b63de4630ee0",
    "f21a508f-ecad-43f5-93c8-3a07592f0f7e", "9c906fac-128e-43f6-9a16-6f355b2294cf",
    "d022f6ae-4a55-4657-873a-b4f5f4487552", "b5c1f0ae-c064-43dd-924c-7e854b3b5b10",
    "dcaae748-5df3-4593-a54a-a8523fc4515b", "9cdbbcd0-2d57-4943-bd97-501f009fa8b8",
)
CASHIER_ACCOUNT_ID = "abf7ead1-1581-40e7-b08c-3b2ecc10e64f"
HISTORICAL_FROM = date(2024, 1, 1)
HISTORICAL_TO = date(2024, 12, 31)
# A full 2025 lets the dashboard compare two closed years with the running year. Its rows are
# provisional chart samples; they invalidate the snapshot's small accepted 2025 coverage (see
# REPORT_READINESS.md) rather than joining it.
COMPARISON_FROM = date(2025, 1, 1)
COMPARISON_TO = date(2025, 12, 31)
CURRENT_FROM = date(2026, 1, 1)
# Fixed through the date of this demo refresh; current rows are operational chart samples only.
CURRENT_TO = date(2026, 11, 5)
WINDOWS = ((HISTORICAL_FROM, HISTORICAL_TO), (COMPARISON_FROM, COMPARISON_TO), (CURRENT_FROM, CURRENT_TO))
BASE_DAILY_VISITS = Decimal("2.1")
# Lunar New Year closures leave a visible gap in the calendar heatmap.
HOLIDAYS = ((date(2024, 2, 8), date(2024, 2, 14)), (date(2025, 1, 27), date(2025, 2, 2)),
            (date(2026, 2, 16), date(2026, 2, 20)))
# Visit volume, exam price and per-year growth differ so department comparisons have a shape.
DEPARTMENT_PROFILES = {
    DEPARTMENT_IDS[0]: {"volume": Decimal("1.30"), "exam": 150000, "lab_rate": 3, "rx_rate": 4,
                        "growth": {2024: "1.00", 2025: "1.12", 2026: "1.24"},
                        "season": ("1.10", "0.80", "1.00", "0.95", "0.90", "0.95",
                                   "1.00", "1.05", "1.10", "1.15", "1.20", "1.15")},
    DEPARTMENT_IDS[1]: {"volume": Decimal("1.05"), "exam": 130000, "lab_rate": 4, "rx_rate": 5,
                        "growth": {2024: "1.00", 2025: "1.18", 2026: "1.10"},
                        "season": ("1.30", "0.95", "1.25", "1.10", "0.85", "0.70",
                                   "0.70", "0.80", "1.05", "1.20", "1.30", "1.35")},
    DEPARTMENT_IDS[2]: {"volume": Decimal("0.70"), "exam": 250000, "lab_rate": 2, "rx_rate": 6,
                        "growth": {2024: "1.00", 2025: "1.06", 2026: "1.30"},
                        "season": ("1.25", "0.85", "1.05", "0.95", "0.90", "0.85",
                                   "0.85", "0.90", "0.95", "1.05", "1.15", "1.30")},
    DEPARTMENT_IDS[3]: {"volume": Decimal("0.85"), "exam": 170000, "lab_rate": 5, "rx_rate": 3,
                        "growth": {2024: "1.00", 2025: "0.94", 2026: "1.08"},
                        "season": ("1.00", "1.05", "1.30", "1.20", "0.90", "0.80",
                                   "0.75", "0.85", "1.25", "1.30", "1.05", "0.95")},
}
WEEKDAY_LOAD = ("1.25", "1.10", "1.00", "0.90", "0.75")
PRICE_GROWTH = {2024: Decimal("1.00"), 2025: Decimal("1.05"), 2026: Decimal("1.11")}


@dataclass(frozen=True)
class Visit:
    source_id: str
    patient_id: str
    department_id: str
    business_date: date
    clinical_category: str
    sequence: int


@dataclass(frozen=True)
class Receipt:
    transaction_id: str
    care_episode_id: str
    patient_id: str
    department_id: str
    business_date: date
    amount: Decimal
    service_purpose: str
    sequence: int


@dataclass(frozen=True)
class Refund:
    refund_transaction_id: str
    original_transaction_id: str
    care_episode_id: str
    patient_id: str
    department_id: str
    business_date: date
    original_business_date: date
    amount: Decimal


@dataclass(frozen=True)
class DemoSeed:
    visits: tuple[Visit, ...]
    receipts: tuple[Receipt, ...]
    refunds: tuple[Refund, ...]
    operational_contributions: tuple[dict, ...]


def _id(label):
    return str(uuid.uuid5(NAMESPACE, label))


def _fingerprint(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _workdays(start, end):
    day = start
    while day <= end:
        if day.weekday() < 5:
            yield day
        day += timedelta(days=1)


def _is_holiday(day):
    return any(start <= day <= end for start, end in HOLIDAYS)


def _unit(label):
    """Deterministic pseudo-random fraction in [0, 1); stable across runs and machines."""
    return Decimal(int(_fingerprint(label)[:8], 16)) / Decimal(2 ** 32)


def _daily_visits(department_id, day):
    """Seasonal, weekday-weighted load, rounded by a stable draw so totals stay smooth."""
    profile = DEPARTMENT_PROFILES[department_id]
    load = (BASE_DAILY_VISITS * profile["volume"] * Decimal(profile["growth"][day.year])
            * Decimal(profile["season"][day.month - 1]) * Decimal(WEEKDAY_LOAD[day.weekday()]))
    return int(load + _unit(f"load:{department_id}:{day}"))


def _money(amount, year):
    """Round a scaled list price to the nearest thousand VND."""
    thousands = (Decimal(amount) * PRICE_GROWTH[year] / 1000).quantize(Decimal("1"))
    return (thousands * 1000).quantize(Decimal("0.01"))


def _instant(day, minute_offset=0):
    # Nine o'clock in Asia/Bangkok is 02:00 UTC; adding the offset stays in the same local day.
    return datetime.combine(day, time(2, 0), timezone.utc) + timedelta(minutes=minute_offset)


def _operational(source_type, source_id, metric_type, department_id, episode_id, business_date,
                 numeric_value, category, label, minute_offset):
    occurred_at = _instant(business_date, minute_offset)
    return {
        "contribution_id": _id("contribution:" + label),
        "event_id": _id("event:" + label),
        "source_type": source_type,
        "source_id": source_id,
        "source_revision": 1,
        "metric_type": metric_type,
        "department_id": department_id,
        "care_episode_type": "OUTPATIENT_VISIT",
        "care_episode_id": episode_id,
        "metric_date": business_date,
        "numeric_value": Decimal(numeric_value),
        "category": category,
        "occurred_at": occurred_at,
        "fact_fingerprint": _fingerprint("report-demo:" + label),
    }


def build_seed():
    """Return the same 2024 acceptance fixture and 2025-2026 chart rows on every call."""
    visits, receipts, refunds, operational = [], [], [], []
    receipt_by_visit = {}
    global_sequence = 0

    for start, end in WINDOWS:
        for business_date in _workdays(start, end):
            if _is_holiday(business_date):
                continue
            for department_index, department_id in enumerate(DEPARTMENT_IDS):
                profile = DEPARTMENT_PROFILES[department_id]
                for within_day in range(_daily_visits(department_id, business_date)):
                    sequence = global_sequence
                    global_sequence += 1
                    episode_id = _id(f"episode:{business_date}:{department_id}:{within_day}")
                    patient_id = PATIENT_IDS[(sequence + department_index * 3) % len(PATIENT_IDS)]
                    category = ("OUTPATIENT_FOLLOW_UP", "PRESCRIPTION", "OTHER")[(sequence + business_date.day) % 3]
                    visit = Visit(episode_id, patient_id, department_id, business_date, category, sequence)
                    visits.append(visit)
                    operational.append(_operational("MEDICAL_RECORD", _id("record:" + episode_id),
                        "COMPLETED_VISITS", department_id, episode_id, business_date, "1", category,
                        "clinical:" + episode_id, 0))

                    year = business_date.year
                    charges = [("EXAM", _money(profile["exam"], year))]
                    if (sequence + business_date.day) % profile["lab_rate"] == 0:
                        charges.append(("LAB_TEST", _money(80000 + (sequence % 3) * 45000, year)))
                        operational.append(_operational("LAB_RESULT", _id("lab:" + episode_id), "LAB_TESTS",
                            department_id, episode_id, business_date, "1", None, "lab:" + episode_id, 25))
                    if (sequence + business_date.day) % profile["rx_rate"] != 0:
                        charges.append(("PRESCRIPTION", _money(60000 + (sequence % 4) * 40000, year)))
                        dispense_id = _id("dispense:" + episode_id)
                        operational.append(_operational("DISPENSE", dispense_id, "DISPENSED_PRESCRIPTIONS",
                            department_id, episode_id, business_date, "1", None, "rx-count:" + episode_id, 50))
                        operational.append(_operational("DISPENSE", dispense_id, "DISPENSED_UNITS",
                            department_id, episode_id, business_date, str(2 + sequence % 5), None,
                            "rx-units:" + episode_id, 50))

                    visit_receipts = []
                    for purpose, amount in charges:
                        transaction_id = _id(f"receipt:{episode_id}:{purpose}")
                        receipt = Receipt(transaction_id, episode_id, patient_id, department_id,
                                          business_date, amount, purpose, sequence)
                        receipts.append(receipt)
                        visit_receipts.append(receipt)
                    receipt_by_visit[episode_id] = visit_receipts

    # A partial cashier refund about every thirteen visits. Refund dates are the next
    # weekday after a two-day processing window, and remain inside their source window.
    for visit in visits:
        end = next(window_end for window_start, window_end in WINDOWS
                   if window_start <= visit.business_date <= window_end)
        if visit.sequence % 13 != 0:
            continue
        candidates = receipt_by_visit[visit.source_id]
        original = candidates[-1]
        refund_date = visit.business_date + timedelta(days=2)
        while refund_date.weekday() >= 5:
            refund_date += timedelta(days=1)
        if refund_date > end:
            continue
        amount = (original.amount * Decimal("0.20")).quantize(Decimal("0.01"))
        refund_id = _id("refund:" + original.transaction_id)
        refunds.append(Refund(refund_id, original.transaction_id, visit.source_id, visit.patient_id,
                              visit.department_id, refund_date, visit.business_date, amount))

    return DemoSeed(tuple(visits), tuple(receipts), tuple(refunds), tuple(operational))


def summarize(seed):
    by_year = {}
    for visit in seed.visits:
        by_year.setdefault(str(visit.business_date.year), {"completedVisits": 0, "receipts": 0, "refunds": 0})
        by_year[str(visit.business_date.year)]["completedVisits"] += 1
    for receipt in seed.receipts:
        by_year[str(receipt.business_date.year)]["receipts"] += 1
    for refund in seed.refunds:
        by_year[str(refund.business_date.year)]["refunds"] += 1
    revenue = {purpose: sum((row.amount for row in seed.receipts if row.service_purpose == purpose), Decimal("0.00"))
               for purpose in ("EXAM", "LAB_TEST", "PRESCRIPTION")}
    return {
        "provenance": "SYNTHETIC_DISPOSABLE_DEMO_PROJECTION_FIXTURE",
        "windows": {
            "historicalReviewFixture": {"fromDate": HISTORICAL_FROM.isoformat(),
                                         "toDate": HISTORICAL_TO.isoformat(), "departmentId": DEPARTMENT_IDS[0]},
            "provisionalComparisonStatistics": {"fromDate": COMPARISON_FROM.isoformat(),
                                                 "toDate": COMPARISON_TO.isoformat(), "departments": list(DEPARTMENT_IDS)},
            "provisionalCurrentStatistics": {"fromDate": CURRENT_FROM.isoformat(),
                                              "toDate": CURRENT_TO.isoformat(), "departments": list(DEPARTMENT_IDS)},
        },
        "counts": {"completedVisits": len(seed.visits), "operationalFacts": len(seed.operational_contributions),
                   "receipts": len(seed.receipts), "refunds": len(seed.refunds)},
        "periods": by_year,
        "revenueVnd": revenue,
        "refundsVnd": sum((row.amount for row in seed.refunds), Decimal("0.00")),
        "reconciled": False,
        "acceptedCoverageAdded": False,
    }


def _sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, (date, datetime)):
        value = value.isoformat()
    if isinstance(value, Decimal):
        value = format(value, "f")
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def _insert(table, columns, rows):
    if not rows:
        return ""
    lines = [f"INSERT INTO {table} ({', '.join(columns)}) VALUES"]
    lines.extend("  (" + ", ".join(_sql(row[column]) for column in columns) + ")" + ("," if index < len(rows) - 1 else "")
                 for index, row in enumerate(rows))
    return "\n".join(lines) + "\nON CONFLICT DO NOTHING;\n"


def _receipt_row(receipt):
    instant = _instant(receipt.business_date, 90)
    iso = instant.astimezone(timezone(timedelta(hours=7))).isoformat(timespec="seconds")
    label = f"{receipt.transaction_id}:{receipt.amount}:{receipt.service_purpose}"
    return {
        "transaction_id": receipt.transaction_id, "first_event_id": _id("receipt-event:" + receipt.transaction_id),
        "invoice_id": None, "payment_request_id": _id("payment-request:" + receipt.transaction_id),
        "account_id": CASHIER_ACCOUNT_ID, "patient_id": receipt.patient_id, "department_id": receipt.department_id,
        "care_episode_type": "OUTPATIENT_VISIT", "care_episode_id": receipt.care_episode_id,
        "classification": "SERVICE_PAYMENT", "amount": receipt.amount, "currency": CURRENCY,
        "payment_method": "CASH" if receipt.sequence % 3 else "TRANSFER", "completed_at": instant,
        "completed_at_iso": iso, "business_date": receipt.business_date, "report_zone": REPORT_ZONE,
        "payload_fingerprint": _fingerprint("payload:" + label), "fact_fingerprint": _fingerprint("fact:" + label),
        "service_purpose": receipt.service_purpose,
    }


def _refund_row(refund):
    instant = _instant(refund.business_date, 120)
    iso = instant.astimezone(timezone(timedelta(hours=7))).isoformat(timespec="seconds")
    label = f"{refund.refund_transaction_id}:{refund.original_transaction_id}:{refund.amount}"
    return {
        "refund_transaction_id": refund.refund_transaction_id,
        "original_transaction_id": refund.original_transaction_id,
        "first_event_id": _id("refund-event:" + refund.refund_transaction_id), "account_id": CASHIER_ACCOUNT_ID,
        "patient_id": refund.patient_id, "department_id": refund.department_id,
        "care_episode_type": "OUTPATIENT_VISIT", "care_episode_id": refund.care_episode_id,
        "amount": refund.amount, "currency": CURRENCY, "completed_at_iso": iso,
        "business_date": refund.business_date, "report_zone": REPORT_ZONE,
        "source_fingerprint": _fingerprint("refund:" + label), "state": "APPLIED",
        "reason_code": "CASHIER_RECORDED_REFUND", "original_business_date": refund.original_business_date,
        "classification": "SERVICE_PAYMENT", "created_at": instant, "applied_at": instant,
        "next_attempt_at": instant, "recovery_attempts": 0,
    }


def render_sql(seed=None):
    seed = seed or build_seed()
    op_columns = ("contribution_id", "event_id", "source_type", "source_id", "source_revision", "metric_type",
                  "department_id", "care_episode_type", "care_episode_id", "metric_date", "numeric_value",
                  "category", "occurred_at", "fact_fingerprint")
    receipt_columns = ("transaction_id", "first_event_id", "invoice_id", "payment_request_id", "account_id",
                       "patient_id", "department_id", "care_episode_type", "care_episode_id", "classification",
                       "amount", "currency", "payment_method", "completed_at", "completed_at_iso", "business_date",
                       "report_zone", "payload_fingerprint", "fact_fingerprint", "service_purpose")
    refund_columns = ("refund_transaction_id", "original_transaction_id", "first_event_id", "account_id", "patient_id",
                      "department_id", "care_episode_type", "care_episode_id", "amount", "currency", "completed_at_iso",
                      "business_date", "report_zone", "source_fingerprint", "state", "reason_code", "original_business_date",
                      "classification", "created_at", "applied_at", "next_attempt_at", "recovery_attempts")
    sql = ["-- Generated by report_demo_seed.py; synthetic Report projections for isolated demo databases only.",
           "-- No upstream source state or report_outpatient_coverage acceptance is created here.", "BEGIN;",
           _insert("operational_contribution", op_columns, list(seed.operational_contributions)),
           _insert("report_cash_receipt", receipt_columns, [_receipt_row(row) for row in seed.receipts]),
           _insert("report_cash_refund", refund_columns, [_refund_row(row) for row in seed.refunds]),
           "COMMIT;", ""]
    return "\n".join(sql)


def export_records(seed, source, start=HISTORICAL_FROM, end=HISTORICAL_TO, department_id=DEPARTMENT_IDS[0]):
    """Build one deterministic reviewed synthetic source export for API reconciliation."""
    dept = str(department_id)
    operational_types = {
        "CLINICAL": {"MEDICAL_RECORD": {"COMPLETED_VISITS"}},
        "LAB": {"LAB_RESULT": {"LAB_TESTS"}},
        "PHARMACY": {"DISPENSE": {"DISPENSED_PRESCRIPTIONS", "DISPENSED_UNITS"}},
    }
    rows = []
    if source in operational_types:
        for fact in seed.operational_contributions:
            if (fact["source_type"] in operational_types[source]
                    and fact["metric_type"] in operational_types[source][fact["source_type"]]
                    and fact["department_id"] == dept and start <= fact["metric_date"] <= end):
                rows.append({key: (value.isoformat() if isinstance(value, (date, datetime)) else str(value) if isinstance(value, Decimal) else value)
                             for key, value in fact.items()})
    elif source == "BILLING_CASH":
        rows = [row.__dict__ | {"business_date": row.business_date.isoformat(), "amount": str(row.amount)}
                for row in seed.receipts if row.department_id == dept and start <= row.business_date <= end]
    elif source == "BILLING_REFUND":
        rows = [row.__dict__ | {"business_date": row.business_date.isoformat(),
                                "original_business_date": row.original_business_date.isoformat(), "amount": str(row.amount)}
                for row in seed.refunds if row.department_id == dept and start <= row.business_date <= end]
    else:
        raise ValueError("Unknown report source: " + source)
    return rows


def export_document(seed, source, start=HISTORICAL_FROM, end=HISTORICAL_TO, department_id=DEPARTMENT_IDS[0]):
    return {
        "provenance": "SYNTHETIC-DEMO-GENERATOR-ONLY-NOT-A-REAL-UPSTREAM-EXPORT",
        "source": source,
        "scope": {"fromDate": start.isoformat(), "toDate": end.isoformat(), "departmentId": str(department_id)},
        "zoneId": REPORT_ZONE,
        "currency": CURRENCY,
        "records": export_records(seed, source, start, end, department_id),
    }


def write_artifacts():
    seed = build_seed()
    ARTIFACT_PATH.write_text(render_sql(seed), encoding="utf-8", newline="\n")
    MANIFEST_PATH.write_text(json.dumps(summarize(seed), ensure_ascii=False, indent=2, default=str) + "\n",
                             encoding="utf-8", newline="\n")
    return summarize(seed)


if __name__ == "__main__":
    print(json.dumps(write_artifacts(), ensure_ascii=False, indent=2, default=str))
