import importlib.util
from pathlib import Path
import unittest
from decimal import Decimal

SEED_PATH = Path(__file__).resolve().parents[1] / 'report_demo_seed.py'
SPEC = importlib.util.spec_from_file_location('report_demo_seed', SEED_PATH)
seed_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(seed_module)


class ReportDemoSeedTest(unittest.TestCase):
    def setUp(self):
        self.seed = seed_module.build_seed()
        self.visits = {visit.source_id: visit for visit in self.seed.visits}
        self.receipts = {receipt.transaction_id: receipt for receipt in self.seed.receipts}

    def test_seed_covers_multiple_recent_months_and_clinical_departments(self):
        months = {visit.business_date.strftime('%Y-%m') for visit in self.seed.visits
                  if visit.business_date.year == 2026}
        departments = {visit.department_id for visit in self.seed.visits}
        dates = {visit.business_date for visit in self.seed.visits if visit.business_date.year == 2026}

        self.assertEqual(months, {f'2026-{month:02d}' for month in range(1, 12)})
        self.assertEqual(departments, set(seed_module.DEPARTMENT_IDS))
        self.assertGreaterEqual(len(dates), 200)
        self.assertGreaterEqual(sum(visit.business_date.year == 2026 for visit in self.seed.visits), 600)
        self.assertTrue(all(day.weekday() < 5 for day in dates))

    def test_years_have_distinct_seasonal_and_department_shapes_for_comparison(self):
        def monthly(year, department):
            counts = {}
            for visit in self.seed.visits:
                if visit.business_date.year == year and visit.department_id == department:
                    counts[visit.business_date.month] = counts.get(visit.business_date.month, 0) + 1
            return counts

        for department in seed_module.DEPARTMENT_IDS:
            for year in (2024, 2025):
                counts = monthly(year, department)
                self.assertEqual(set(counts), set(range(1, 13)))
                self.assertGreaterEqual(max(counts.values()), min(counts.values()) * 1.3)
            self.assertNotEqual(monthly(2024, department), monthly(2025, department))
        general = seed_module.DEPARTMENT_IDS[0]
        self.assertGreater(sum(monthly(2025, general).values()), sum(monthly(2024, general).values()))
        ent = seed_module.DEPARTMENT_IDS[3]
        self.assertLess(sum(monthly(2025, ent).values()), sum(monthly(2024, ent).values()))

    def test_no_visit_falls_on_a_lunar_new_year_closure(self):
        self.assertFalse(any(seed_module._is_holiday(visit.business_date) for visit in self.seed.visits))

    def test_receipts_are_classified_and_link_to_their_visit(self):
        self.assertEqual({receipt.service_purpose for receipt in self.seed.receipts},
                         {'EXAM', 'LAB_TEST', 'PRESCRIPTION'})

        for receipt in self.seed.receipts:
            visit = self.visits[receipt.care_episode_id]
            self.assertEqual(receipt.patient_id, visit.patient_id)
            self.assertEqual(receipt.department_id, visit.department_id)
            self.assertEqual(receipt.business_date, visit.business_date)

    def test_applied_refunds_are_partial_and_link_to_the_original_receipt(self):
        refunds_by_receipt = {}
        for refund in self.seed.refunds:
            receipt = self.receipts[refund.original_transaction_id]
            self.assertEqual(refund.care_episode_id, receipt.care_episode_id)
            self.assertEqual(refund.patient_id, receipt.patient_id)
            self.assertEqual(refund.department_id, receipt.department_id)
            self.assertGreaterEqual(refund.business_date, receipt.business_date)
            self.assertLess(refund.amount, receipt.amount)
            refunds_by_receipt[receipt.transaction_id] = (
                refunds_by_receipt.get(receipt.transaction_id, 0) + refund.amount)

        self.assertGreaterEqual(len(self.seed.refunds), 20)
        self.assertTrue(all(amount <= self.receipts[transaction_id].amount
                            for transaction_id, amount in refunds_by_receipt.items()))

    def test_manifest_reports_calculated_counts_and_provisional_totals(self):
        manifest = seed_module.summarize(self.seed)
        totals = {
            line: sum(receipt.amount for receipt in self.seed.receipts
                      if receipt.service_purpose == line)
            for line in ('EXAM', 'LAB_TEST', 'PRESCRIPTION')
        }
        refund_total = sum(refund.amount for refund in self.seed.refunds)

        self.assertEqual(manifest['counts']['completedVisits'], len(self.seed.visits))
        self.assertEqual(manifest['counts']['receipts'], len(self.seed.receipts))
        self.assertEqual(manifest['counts']['refunds'], len(self.seed.refunds))
        self.assertEqual({key: Decimal(value) for key, value in manifest['revenueVnd'].items()}, totals)
        self.assertEqual(Decimal(manifest['refundsVnd']), refund_total)
        self.assertEqual(set(manifest['periods']), {'2024', '2025', '2026'})
        self.assertFalse(manifest['reconciled'])
        self.assertFalse(manifest['acceptedCoverageAdded'])

    def test_historical_review_fixture_is_a_full_closed_year_with_all_five_sources(self):
        self.assertEqual(seed_module.HISTORICAL_FROM.isoformat(), '2024-01-01')
        self.assertEqual(seed_module.HISTORICAL_TO.isoformat(), '2024-12-31')
        self.assertGreaterEqual(sum(visit.business_date.year == 2024 for visit in self.seed.visits), 1800)
        for source in ('CLINICAL', 'LAB', 'PHARMACY', 'BILLING_CASH', 'BILLING_REFUND'):
            records = seed_module.export_records(self.seed, source)
            self.assertGreater(len(records), 0, source)
            document = seed_module.export_document(self.seed, source)
            self.assertEqual(document['provenance'], 'SYNTHETIC-DEMO-GENERATOR-ONLY-NOT-A-REAL-UPSTREAM-EXPORT')
            self.assertEqual(document['records'], records)

    def test_sql_artifact_is_repeatable_idempotent_and_never_accepts_coverage(self):
        sql = seed_module.render_sql(self.seed)
        self.assertEqual(sql, seed_module.render_sql(seed_module.build_seed()))
        self.assertEqual(sql, seed_module.ARTIFACT_PATH.read_text(encoding='utf-8'))
        self.assertIn('ON CONFLICT DO NOTHING', sql)
        self.assertIn('BEGIN;', sql)
        self.assertIn('COMMIT;', sql)
        self.assertNotIn('INSERT INTO report_outpatient_coverage', sql)
        self.assertNotIn('DELETE FROM', sql.upper())
        self.assertIn("'EXAM'", sql)
        self.assertIn("'LAB_TEST'", sql)
        self.assertIn("'PRESCRIPTION'", sql)


if __name__ == '__main__':
    unittest.main()
