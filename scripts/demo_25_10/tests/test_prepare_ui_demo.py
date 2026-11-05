import datetime as dt
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('prepare_ui_demo', Path(__file__).resolve().parents[1] / 'prepare_ui_demo.py')
demo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(demo)


class CancellationTest(unittest.TestCase):
    def test_stop_request_prevents_another_preparation_write(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stop = root / 'stop'
            stop.touch()
            api = demo.DemoApi('http://127.0.0.1:28080', root / 'journal.json', stop)
            with patch.object(demo.urllib.request, 'urlopen') as request:
                with self.assertRaises(InterruptedError):
                    api.call('admin', 'POST', '/api/v2/consultation-sessions', {}, stage='fixture')
                request.assert_not_called()
            self.assertFalse((root / 'journal.json').exists())


class FakeApi:
    def __init__(self):
        self.state, self.claims, self.calls, self.sessions = {}, {}, [], []
        self.logins = []
        self.record = None
        self.registration_status = 'CONFIRMED'
        self.payment_status = 'COMPLETED'

    def login(self, role, username):
        self.logins.append((role, username))
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
    def test_primary_doctor_never_occupies_the_rotating_calendar_room(self):
        api = FakeApi()
        original = api.call
        def call(role, method, path, *args, **kwargs):
            if path.startswith('/api/v2/org/consultation-rooms?'):
                return {'content': [{'roomId': 'calendar-room', 'roomName': 'Phong kham lich day demo'},
                                    {'roomId': 'room', 'roomName': 'Original demo room'}]}
            return original(role, method, path, *args, **kwargs)
        api.call = call
        result = demo.prepare(api, dt.datetime(2026, 11, 1, 18, tzinfo=demo.ZONE))
        self.assertEqual(result['room']['roomId'], 'room')
        self.assertTrue(all(row['roomId'] == 'room' for row in api.sessions))

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
        self.assertEqual(api.logins, [
            ('admin', 'demo.staff01@example.test'),
            ('nurse', 'demo.staff04@example.test'),
            ('doctor', 'demo.staff03@example.test'),
            ('cashier', 'demo.staff19@example.test'),
            ('patient', 'demo.patient01@example.test'),
            ('patient2', 'demo.patient02@example.test'),
        ])
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


class DemoApiTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.api = demo.DemoApi('http://127.0.0.1:28080', Path(folder.name) / 'journal.json')

    def response(self, status, envelope):
        stream = io.BytesIO(json.dumps(envelope).encode())
        if status >= 400:
            return demo.urllib.error.HTTPError(self.api.gateway, status, 'not ready', {}, stream)
        stream.status = status
        return stream

    def test_account_retry_uses_generated_credential_contract_without_password(self):
        body = {'username': 'roster.example.bs01@example.test', 'staffId': 'staff-1',
                'patientId': None, 'role': 'DOCTOR'}
        path = '/api/v1/org/accounts'
        with patch.object(demo.urllib.request, 'urlopen', side_effect=TimeoutError('uncertain')) as first:
            with self.assertRaises(TimeoutError):
                self.api.call('admin', 'POST', path, body, stage='account')
        reopened = demo.DemoApi(self.api.gateway, self.api.journal)
        mail_delivery = {'messageId': 'mail-id', 'status': 'PENDING'}
        with patch.object(demo.urllib.request, 'urlopen', return_value=self.response(201, {
                'data': {'accountId': 'one', 'username': body['username'], 'mailDelivery': mail_delivery}})) as retry:
            result = reopened.call('admin', 'POST', path, body, expected=(201,), stage='account')
        self.assertEqual(first.call_args.args[0].data, retry.call_args.args[0].data)
        self.assertEqual(first.call_args.args[0].get_header('Idempotency-key'), retry.call_args.args[0].get_header('Idempotency-key'))
        saved = json.loads(reopened.journal.read_text())['account']
        self.assertNotIn('password', saved['body'])
        self.assertNotIn('password', saved['result'])
        self.assertEqual(result['mailDelivery'], mail_delivery)

    def test_wait_skips_expected_http_errors_before_evaluating_data(self):
        ready = {'paymentRequestId': 'request', 'reservationId': 'reservation', 'reservationRevision': 3}
        responses = [self.response(404, {'success': False, 'error': {'code': 'NOT_FOUND'}}),
                     self.response(503, {'success': False, 'error': {'code': 'UNAVAILABLE'}}),
                     self.response(200, {'success': True, 'data': ready})]
        observed = []
        with patch.object(demo.urllib.request, 'urlopen', side_effect=responses) as send, \
                patch.object(demo.time, 'sleep') as sleep:
            result = self.api.wait('cashier', '/payment-requests/by-registration',
                                   lambda row: observed.append(row) or True, expected=(200, 404, 503))
        self.assertEqual(result, ready)
        self.assertEqual(observed, [ready])
        self.assertEqual(send.call_count, 3)
        self.assertEqual(sleep.call_count, 2)

    def test_wait_times_out_when_expected_errors_never_become_data(self):
        def unavailable(*args, **kwargs):
            raise self.response(503, {'success': False, 'error': {'code': 'UNAVAILABLE'}})

        with patch.object(demo.urllib.request, 'urlopen', side_effect=unavailable) as send, \
                patch.object(demo.time, 'sleep'), \
                self.assertRaisesRegex(RuntimeError, 'Projection did not become ready'):
            self.api.wait('cashier', '/payment-requests/by-registration', lambda row: True,
                          expected=(200, 404, 503))
        self.assertEqual(send.call_count, 45)

    def test_wait_raises_unexpected_status_without_retrying(self):
        for status in (401, 403, 500):
            with self.subTest(status=status), \
                    patch.object(demo.urllib.request, 'urlopen', side_effect=self.response(status, {'error': {}})) as send, \
                    patch.object(demo.time, 'sleep') as sleep, self.assertRaises(demo.ApiError) as raised:
                self.api.wait('cashier', '/payment-requests/by-registration', lambda row: True,
                              expected=(200, 404, 503))
            self.assertEqual(raised.exception.status, status)
            send.assert_called_once()
            sleep.assert_not_called()

    def test_expected_write_error_keeps_identity_without_caching_success(self):
        body = {'reservationId': 'reservation', 'expectedReservationRevision': 3, 'quoteId': 'quote'}
        path = '/payment-requests/request/payments'
        with patch.object(demo.urllib.request, 'urlopen',
                          side_effect=self.response(503, {'error': {'code': 'UNAVAILABLE'}})) as first_send:
            self.assertIsNone(self.api.call('cashier', 'POST', path, body, expected=(200, 503), stage='payment'))
        saved = json.loads(self.api.journal.read_text(encoding='utf-8'))['payment']
        self.assertNotIn('result', saved)
        self.assertEqual(saved['body'], body)
        self.assertEqual(saved['path'], path)
        reopened = demo.DemoApi(self.api.gateway, self.api.journal)
        ready = {'operation': {'status': 'COMPLETED'}}
        with patch.object(demo.urllib.request, 'urlopen', return_value=self.response(200, {'data': ready})) as retry_send:
            self.assertEqual(reopened.call('cashier', 'POST', path, body, stage='payment'), ready)
        first_request = first_send.call_args.args[0]
        retry_request = retry_send.call_args.args[0]
        self.assertEqual(first_request.get_header('Idempotency-key'), saved['key'])
        self.assertEqual(retry_request.get_header('Idempotency-key'), saved['key'])
        self.assertEqual(first_request.data, retry_request.data)
        self.assertEqual(reopened.state['payment']['result'], ready)
        with patch.object(demo.urllib.request, 'urlopen', side_effect=AssertionError('must not resend')):
            self.assertEqual(reopened.call('cashier', 'POST', path, body, stage='payment'), ready)
            with self.assertRaises(ValueError):
                reopened.call('cashier', 'POST', path + '/changed', body, stage='payment')


if __name__ == '__main__':
    unittest.main()
