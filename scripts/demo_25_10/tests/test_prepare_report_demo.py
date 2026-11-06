import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import sys
from datetime import datetime, timezone
from unittest.mock import Mock, patch

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
SEED_SPEC = importlib.util.spec_from_file_location('report_demo_seed_for_acceptance', HERE / 'report_demo_seed.py')
seed = importlib.util.module_from_spec(SEED_SPEC)
SEED_SPEC.loader.exec_module(seed)
PREP_SPEC = importlib.util.spec_from_file_location('prepare_report_demo', HERE / 'prepare_report_demo.py')
prep = importlib.util.module_from_spec(PREP_SPEC)
PREP_SPEC.loader.exec_module(prep)
from prepare_ui_demo import ApiError


HEAD_STAFF = '52977f14-bb6e-4a6f-8474-edae4aeb9879'
HEAD_DEPARTMENT = '9a6dd24a-1c1f-4122-9e31-7cfea345ec45'


class FakeApi:
    def __init__(self, pending=None):
        self.pending = pending
        self.calls = []
        self.logins = []
        self.accepted = []
        self.claims = {'doctor': {'staffId': HEAD_STAFF}}
        self.head = None

    def login(self, role, username):
        self.logins.append((role, username))

    def call(self, role, method, path, body=None, **kwargs):
        self.calls.append((role, method, path, body))
        if path == '/api/v1/org/staff/' + HEAD_STAFF:
            return {'departmentId': HEAD_DEPARTMENT}
        if path.endswith('/head-appointments'):
            if method == 'PUT':
                self.head = body['staffId']
            return {'revision': 0, 'staffId': self.head}
        if path.startswith('/api/v1/reports/outpatient/coverage/preview'):
            sources = {
                name: {'rowCount': len(seed.export_records(seed.build_seed(), name)), 'fingerprint': 'a' * 64}
                for name in seed.EXPORT_SOURCES
            }
            return {'fingerprint': 'f' * 64, 'pendingOrInvalid': self.pending or False, 'sources': sources}
        if path.startswith('/api/v1/reports/outpatient/available-periods'):
            periods = [{'fromDate': '2025-01-01', 'toDate': '2025-12-31',
                        'departmentId': seed.DEPARTMENT_IDS[0], 'syntheticData': True}]
            if self.accepted:
                periods.append({'fromDate': '2024-01-01', 'toDate': '2024-12-31',
                                'departmentId': seed.DEPARTMENT_IDS[0], 'syntheticData': True})
            return periods
        if path == '/api/v1/reports/outpatient/coverage/acceptances':
            self.accepted.append(body)
            return {'coverageId': 'coverage-id', 'from': '2024-01-01', 'to': '2024-12-31',
                    'departmentId': seed.DEPARTMENT_IDS[0], 'acceptanceReference': body['acceptanceReference']}
        if path.startswith('/api/v1/reports/outpatient/daily'):
            fixture = seed.build_seed()
            visits = sum(visit.department_id == seed.DEPARTMENT_IDS[0] and visit.business_date == prep.DAILY_SAMPLE_DATE
                         for visit in fixture.visits)
            return {'covered': True, 'periods': [{'completedVisits': visits, 'cashReceived': 230000, 'refunds': 30000}]}
        if path.startswith('/api/v1/reports/outpatient/monthly'):
            return {'covered': True, 'periods': [{} for _ in range(12)]}
        if path.startswith('/api/v1/reports/outpatient/statistics'):
            current = sum(visit.business_date.year == seed.CURRENT_TO.year for visit in seed.build_seed().visits)
            return {'reconciled': False, 'totals': {'completedVisits': current},
                    'days': [{}], 'departments': [{} for _ in seed.DEPARTMENT_IDS]}
        raise AssertionError('Unexpected API path: ' + path)


class PrepareReportDemoTest(unittest.TestCase):
    def test_the_seeded_doctor_is_appointed_head_once_through_the_admin_api(self):
        api = FakeApi()
        prep.appoint_department_head(api)
        prep.appoint_department_head(api)
        writes = [call for call in api.calls if call[1] == 'PUT']
        self.assertEqual(len(writes), 1)
        self.assertEqual(writes[0][0], 'admin')
        self.assertIn(HEAD_DEPARTMENT, writes[0][2])
        self.assertEqual(writes[0][3]['staffId'], HEAD_STAFF)

    def test_default_preview_writes_five_explicit_synthetic_exports_without_accepting(self):
        api = FakeApi()
        with tempfile.TemporaryDirectory() as temporary:
            result = prep.prepare(api, Path(temporary), accept=False,
                                  now=datetime(2026, 11, 5, 12, tzinfo=timezone.utc))
            source_dir = Path(temporary) / 'report-source-exports' / '2024-annual'
            self.assertEqual({path.stem for path in source_dir.glob('*.json')}, set(seed.EXPORT_SOURCES))
            self.assertEqual(len(api.accepted), 0)
            self.assertFalse(result['accepted'])
            self.assertEqual(result['availableSyntheticPeriods'], 1)
            proof = json.loads((source_dir / 'CLINICAL.json').read_text(encoding='utf-8'))
            self.assertTrue(proof['provenance'].startswith('SYNTHETIC-DEMO-'))
        self.assertEqual(api.logins[0], ('admin', prep.SEED_LOGIN_EMAILS['admin']))

    def test_explicit_accept_posts_reviewed_counts_then_verifies_reads_and_period_list(self):
        api = FakeApi()
        with tempfile.TemporaryDirectory() as temporary:
            result = prep.prepare(api, Path(temporary), accept=True,
                                  now=datetime(2026, 11, 5, 12, tzinfo=timezone.utc))
        self.assertEqual(len(api.accepted), 1)
        request = api.accepted[0]
        self.assertEqual(set(request['sources']), set(seed.EXPORT_SOURCES))
        self.assertTrue(all(source['reference'].startswith('SYNTHETIC-DEMO-')
                            for source in request['sources'].values()))
        self.assertEqual(result['acceptedCoverage']['coverageId'], 'coverage-id')
        self.assertEqual(result['availableSyntheticPeriods'], 2)
        self.assertTrue(result['historicalDailyRead']['covered'])
        self.assertTrue(result['historicalMonthlyRead']['covered'])
        self.assertFalse(result['currentStatistics']['reconciled'])
        self.assertTrue(any(call[2].startswith('/api/v1/reports/outpatient/coverage/acceptances')
                            for call in api.calls))

    def test_pending_or_invalid_local_evidence_refuses_acceptance(self):
        api = FakeApi(pending=True)
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(RuntimeError, 'pending/invalid'):
                prep.prepare(api, Path(temporary), accept=True,
                             now=datetime(2026, 11, 5, 12, tzinfo=timezone.utc))
        self.assertEqual(api.accepted, [])

    def test_export_row_count_mismatch_refuses_acceptance(self):
        api = FakeApi()
        api.call = Mock(side_effect=api.call)
        def mismatch(role, method, path, body=None, **kwargs):
            result = FakeApi.call(api, role, method, path, body, **kwargs)
            if path.startswith('/api/v1/reports/outpatient/coverage/preview'):
                result['sources']['LAB']['rowCount'] += 1
            return result
        api.call.side_effect = mismatch
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(RuntimeError, 'does not match'):
                prep.prepare(api, Path(temporary), accept=True,
                             now=datetime(2026, 11, 5, 12, tzinfo=timezone.utc))
        self.assertEqual(api.accepted, [])

    def test_transient_gateway_unavailable_preview_retries_then_continues(self):
        api = FakeApi()
        original_call = api.call
        preview_attempts = 0

        def unavailable_once(role, method, path, body=None, **kwargs):
            nonlocal preview_attempts
            if path.startswith('/api/v1/reports/outpatient/coverage/preview'):
                preview_attempts += 1
                if preview_attempts == 1:
                    raise ApiError(503, "GET preview: HTTP 503, {'code': 'GATEWAY_UPSTREAM_UNAVAILABLE'}")
            return original_call(role, method, path, body, **kwargs)

        api.call = unavailable_once
        with tempfile.TemporaryDirectory() as temporary, patch('time.sleep') as sleep:
            result = prep.prepare(api, Path(temporary), accept=False,
                                  now=datetime(2026, 11, 5, 12, tzinfo=timezone.utc))
        self.assertEqual(preview_attempts, 2)
        self.assertEqual(sleep.call_count, 1)
        self.assertFalse(result['accepted'])

    def test_non_gateway_preview_failure_is_not_retried(self):
        api = FakeApi()
        original_call = api.call
        preview_attempts = 0

        def forbidden(role, method, path, body=None, **kwargs):
            nonlocal preview_attempts
            if path.startswith('/api/v1/reports/outpatient/coverage/preview'):
                preview_attempts += 1
                raise ApiError(503, "GET preview: HTTP 503, {'code': 'REPORT_PREVIEW_FAILED'}")
            return original_call(role, method, path, body, **kwargs)

        api.call = forbidden
        with tempfile.TemporaryDirectory() as temporary, patch('time.sleep') as sleep:
            with self.assertRaisesRegex(ApiError, 'REPORT_PREVIEW_FAILED'):
                prep.prepare(api, Path(temporary), accept=True,
                             now=datetime(2026, 11, 5, 12, tzinfo=timezone.utc))
        self.assertEqual(preview_attempts, 1)
        sleep.assert_not_called()
        self.assertEqual(api.accepted, [])


if __name__ == '__main__':
    unittest.main()
