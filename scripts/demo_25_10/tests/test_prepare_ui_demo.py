import datetime as dt
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('prepare_ui_demo', Path(__file__).resolve().parents[1] / 'prepare_ui_demo.py')
demo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(demo)


class FakeApi:
    def __init__(self):
        self.state, self.claims, self.calls, self.sessions = {}, {}, [], []
        self.record = None
        self.registration_status = 'CONFIRMED'
        self.payment_status = 'COMPLETED'

    def login(self, role, username):
        self.claims[role] = {'staffId': 'doctor-id', 'patientId': role + '-id'}

    def call(self, role, method, path, body=None, expected=(200,), stage=None):
        self.calls.append((role, method, path, body))
        if path.startswith('/api/v1/org/staff/'):
            return {'departmentId': 'dept', 'fullName': 'Demo doctor'}
        if path.startswith('/api/v1/org/departments/'):
            return {'departmentId': 'dept', 'departmentName': 'Demo department'}
        if path.startswith('/api/v1/patients/'):
            return {'maBenhNhan': path.rsplit('/', 1)[1], 'hoTen': 'Demo', 'soCmnd': '123'}
        if path.startswith('/api/v2/org/consultation-rooms?'):
            return {'content': [{'roomId': 'room', 'roomName': 'Demo room'}]}
        if path == '/api/v2/consultation-sessions':
            result = {**body, 'sessionId': 'session-' + body['sessionDate'], 'revision': 1}
            self.sessions.append(result)
            return result
        if path.startswith('/api/v2/consultation-sessions?'):
            return {'content': self.sessions}
        if path == '/api/v2/outpatient-registrations':
            return {'registrationId': 'history'}
        if path.startswith('/api/v2/outpatient-registrations/follow-up-sources?'):
            return {'content': [{'registrationId': 'history'}]}
        if path == '/api/v2/outpatient-registrations/history':
            return {'status': self.registration_status}
        if path.endswith('/quote'):
            return {'quoteId': 'quote', 'patientPayable': '50000.00'}
        if path.endswith('/payments'):
            return {'operation': {'status': self.payment_status}}
        if path.startswith('/api/v1/records/patient/'):
            return [self.record] if self.record else []
        if path.endswith('/start-exam'):
            self.record = {'recordId': 'record', 'appointmentId': 'history', 'status': 'OPEN', 'diagnoses': []}
            return {}
        if path == '/api/v1/records/record':
            return self.record
        if path.endswith('/diagnoses'):
            self.record['diagnoses'] = [body]
            return body
        if path.endswith('/complete'):
            self.record['status'] = 'COMPLETED'
            return self.record
        if path.endswith('/follow-up-recommendations'):
            return {**body, 'sourceRegistrationId': 'history'}
        raise AssertionError((method, path))

    def wait(self, role, path, predicate, expected=(200,)):
        if 'by-registration?' in path:
            return {'paymentRequestId': 'request', 'reservationId': 'reservation', 'reservationRevision': 3}
        if path == '/api/v2/outpatient-registrations/history':
            self.registration_status = 'CONFIRMED'
            return {'status': self.registration_status}
        raise AssertionError(path)


class PreparationTest(unittest.TestCase):
    def test_day_is_vietnam_day_at_utc_midnight_boundary(self):
        today, tomorrow = demo.demo_dates(dt.datetime(2026, 10, 28, 18, tzinfo=dt.timezone.utc))
        self.assertEqual(str(today), '2026-10-29')
        self.assertEqual(str(tomorrow), '2026-10-30')

    def test_history_window_has_end_margin(self):
        for hour, minute, expected in [(6, 59, False), (7, 0, True), (16, 44, True), (16, 45, False), (17, 0, False)]:
            self.assertEqual(demo.can_prepare_history(dt.datetime(2026, 10, 28, hour, minute, tzinfo=demo.ZONE)), expected)

    def test_after_hours_only_tomorrow_no_patient_registration_or_payment(self):
        api = FakeApi()
        report = demo.prepare(api, dt.datetime(2026, 10, 28, 18, tzinfo=demo.ZONE))
        self.assertFalse(report['historyReady'])
        self.assertEqual([row['sessionDate'] for row in report['sessions']], ['2026-10-29'])
        self.assertTrue(all('outpatient-registrations' not in path for _, _, path, _ in api.calls))

    def test_full_preparation_completes_only_patient2_and_leaves_demo_cases_unbooked(self):
        api = FakeApi()
        report = demo.prepare(api, dt.datetime(2026, 10, 28, 9, tzinfo=demo.ZONE))
        self.assertTrue(report['historyReady'])
        registrations = [body for _, method, path, body in api.calls if method == 'POST' and path == '/api/v2/outpatient-registrations']
        self.assertEqual(len(registrations), 1)
        self.assertEqual(registrations[0]['patientId'], 'patient2-id')
        self.assertEqual(api.record['status'], 'COMPLETED')
        self.assertEqual(len(report['sessions']), 2)
        self.assertEqual(report['followUpAdvice']['recommendedFrom'], '2026-10-28')

    def test_stopped_or_foreign_manifest_refused(self):
        with self.assertRaises(ValueError):
            demo.validate_manifest({'gateway': 'https://example.com', 'scheduling': True,
                                    'evidence': str(demo.HERE / 'evidence/test'), 'stopFile': '/tmp/stop'})

    def test_pending_history_is_paid_with_authoritative_quote_before_exam(self):
        api = FakeApi()
        api.registration_status = 'AWAITING_PAYMENT'
        report = demo.prepare(api, dt.datetime(2026, 10, 28, 9, tzinfo=demo.ZONE))
        self.assertTrue(report['historyReady'])
        payments = [body for _, method, path, body in api.calls if method == 'POST' and path.endswith('/payments')]
        self.assertEqual(payments, [{'reservationId': 'reservation', 'expectedReservationRevision': 3,
                                     'quoteId': 'quote', 'amount': '50000.00', 'currency': 'VND', 'paymentMethod': 'CASH'}])

    def test_unsuccessful_payment_cannot_create_completed_history(self):
        api = FakeApi()
        api.registration_status = 'AWAITING_PAYMENT'
        api.payment_status = 'CLOSED_NOT_PAID'
        with self.assertRaisesRegex(RuntimeError, 'payment did not complete'):
            demo.prepare(api, dt.datetime(2026, 10, 28, 9, tzinfo=demo.ZONE))
        self.assertIsNone(api.record)

    def test_journal_replays_command_without_second_http_and_rejects_changed_body(self):
        with tempfile.TemporaryDirectory() as folder:
            api = demo.DemoApi('http://127.0.0.1:28080', Path(folder) / 'journal.json')
            api.state['session'] = {'key': 'key', 'body': {'capacity': 40}, 'path': '/api/session', 'result': {'id': 'one'}}
            api.save()
            reopened = demo.DemoApi(api.gateway, api.journal)
            with patch.object(demo.urllib.request, 'urlopen', side_effect=AssertionError('must not resend')):
                self.assertEqual(reopened.call('admin', 'POST', '/api/session', {'capacity': 40}, stage='session'), {'id': 'one'})
                with self.assertRaises(ValueError):
                    reopened.call('admin', 'POST', '/api/session', {'capacity': 41}, stage='session')


if __name__ == '__main__':
    unittest.main()
