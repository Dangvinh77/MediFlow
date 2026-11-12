import datetime as dt
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_cashier_demo as cashier
import prepare_ui_demo as ui

NOW = dt.datetime(2026, 11, 7, 9, tzinfo=ui.ZONE)
SESSION = {'sessionId': 'session', 'sessionDate': '2026-11-07', 'departmentId': 'dept', 'doctorId': 'doctor', 'revision': 1}
FIXTURES = {'historyReady': True, 'sessions': [SESSION]}


class FakeApi:
    def __init__(self):
        self.state, self.claims, self.calls, self.logins = {}, {}, [], []
        self.records, self.revision, self.dispense_refusals = {}, 1, 0
        self.lab_status = 'AWAITING_PAYMENT'

    def login(self, role, username):
        self.logins.append(username)
        self.claims[role] = {'patientId': role + '-patient'}

    def wait(self, role, path, predicate, expected=(200,)):
        self.calls.append((role, 'WAIT', path, None))
        return {'paymentRequestId': 'request', 'remainingAmount': 20000}

    def call(self, role, method, path, body=None, expected=(200,), stage=None):
        self.calls.append((role, method, path, body))
        if path.startswith('/api/v2/consultation-sessions?'):
            return {'content': [{**SESSION, 'revision': self.revision}]}
        if path == '/api/v2/outpatient-registrations':
            self.revision += 1
            return {'registrationId': 'visit-' + body['patientId']}
        if path.startswith('/api/v2/outpatient-registrations/'):
            return {'status': 'CONFIRMED'}
        if path.startswith('/api/v1/records/patient/'):
            patient = path.rsplit('/', 1)[1]
            return [self.records[patient]] if patient in self.records else []
        if path.endswith('/start-exam'):
            visit = path.split('/')[-2]
            patient = visit.removeprefix('visit-')
            self.records[patient] = {'recordId': 'record-' + patient, 'appointmentId': visit, 'status': 'OPEN', 'diagnoses': []}
            return {}
        if path.endswith('/diagnoses'):
            return body
        if path.endswith('/complete'):
            return {}
        if path.endswith('/lab-orders'):
            return {'sourceOrderId': 'order'}
        if path.startswith('/api/v1/records/'):
            return next(row for row in self.records.values() if row['recordId'] == path.rsplit('/', 1)[1])
        if path.startswith('/api/v1/pharmacy/drugs?'):
            return {'content': [{'drugId': 'wrong', 'drugName': 'DEMO-HIST-2025 — Thuốc hết tồn'},
                                {'drugId': 'drug', 'drugName': 'DEMO-HIST-2025 — Thuốc minh họa A'}]}
        if path == '/api/v1/lab':
            return {'testId': 'test-' + body['patientId']}
        if path.endswith('/quote'):
            return {'quoteId': 'quote', 'patientPayable': 36000}
        if path.endswith('/results'):
            raise AssertionError('catalogue CBC must go through second-person review, not v1 results')
        if path.endswith('/start'):
            self.lab_status = 'IN_PROGRESS'
            return {}
        if path.endswith('/specimens') and method == 'GET':
            return []
        if path.endswith('/specimens'):
            return {'specimenId': 'specimen'}
        if path.endswith('/decision'):
            return {'decision': 'ACCEPTED'}
        if path.endswith('/report') and method == 'GET':
            return None  # 404 before the first draft
        if path.endswith('/report/draft'):
            return {'status': 'DRAFT', 'draftRevision': 1}
        if path.endswith('/report/submission'):
            return {'status': 'PENDING_REVIEW', 'draftRevision': 1}
        if path.endswith('/report/release'):
            self.lab_status = 'COMPLETED'
            return {'status': 'RELEASED'}
        if path.startswith('/api/v1/lab/'):
            return {'clearanceId': 'clearance', 'status': 'READY' if self.lab_status == 'AWAITING_PAYMENT' else self.lab_status}
        if path == '/api/v1/pharmacy/prescriptions':
            return {'prescriptionId': 'rx-' + body['patientId']}
        if path.endswith('/dispense'):
            if self.dispense_refusals:
                self.dispense_refusals -= 1
                return None
            return {'dispenseId': 'dispense'}
        if path.endswith('/lab-payments') or path.endswith('/payments'):
            return {'transactionId': 'transaction'}
        raise AssertionError((method, path))

    def posts(self, suffix):
        return [row for row in self.calls if row[1] == 'POST' and row[2].endswith(suffix)]


class CashierDemoTest(unittest.TestCase):
    def test_paid_visit_is_collected_and_open_visit_stays_payable(self):
        api = FakeApi()
        result = cashier.prepare(api, FIXTURES, NOW)
        self.assertTrue(result['ready'])
        self.assertEqual(len(api.posts('/lab-payments')), 1)
        self.assertEqual(len(api.posts('/payments')), 1)
        self.assertEqual(len(api.posts('/api/v1/lab')), 2)
        self.assertEqual(len(api.posts('/api/v1/pharmacy/prescriptions')), 2)
        self.assertEqual([row[2] for row in api.calls if row[2].endswith('/complete')],
                         ['/api/v1/records/record-paid-patient/complete'])
        self.assertEqual([row[2] for row in api.calls if row[2].endswith('/dispense')],
                         ['/api/v1/pharmacy/prescriptions/rx-paid-patient/dispense'])
        self.assertEqual(result['open']['prescriptionId'], 'rx-open-patient')
        self.assertEqual(result['open']['prescriptionPayable'], 20000)

    def test_cbc_is_ordered_from_the_catalogue_and_released_by_the_second_lab_tech(self):
        api = FakeApi()
        cashier.prepare(api, FIXTURES, NOW)
        for _, _, _, body in api.posts('/lab-orders') + api.posts('/api/v1/lab'):
            self.assertEqual((body['serviceCode'], body['priceCode'], body['labType']), ('CBC', 'LAB-CBC', 'Công thức máu'))
        self.assertIn('demo.staff16@example.test', api.logins)
        draft = next(row for row in api.calls if row[2].endswith('/report/draft'))
        self.assertEqual(draft[0], 'lab')
        self.assertEqual({v['fieldCode'] for v in draft[3]['values']}, {'WBC', 'RBC', 'HGB', 'HCT', 'PLT'})
        releases = [row for row in api.calls if row[2].endswith('/report/release')]
        self.assertEqual([(row[0], row[2]) for row in releases], [('lab2', '/api/v2/lab/test-paid-patient/report/release')])

    def test_each_registration_uses_the_current_session_revision(self):
        api = FakeApi()
        cashier.prepare(api, FIXTURES, NOW)
        self.assertEqual([row[3]['expectedSessionRevision'] for row in api.posts('/outpatient-registrations')], [1, 2])

    def test_prescription_uses_the_stocked_drug_and_outpatient_contract(self):
        api = FakeApi()
        cashier.prepare(api, FIXTURES, NOW)
        body = api.posts('/api/v1/pharmacy/prescriptions')[0][3]
        self.assertEqual(body['lines'][0]['drugId'], 'drug')
        self.assertEqual(body['priceCode'], 'RX-DEMO')
        self.assertEqual(body['careEpisodeId'], 'visit-paid-patient')

    def test_dispense_waits_for_clearance_then_gives_up_loudly(self):
        api = FakeApi()
        api.dispense_refusals = 3
        with patch.object(cashier.time, 'sleep'):
            cashier.prepare(api, FIXTURES, NOW)
        api = FakeApi()
        api.dispense_refusals = cashier.READY_ATTEMPTS
        with patch.object(cashier.time, 'sleep'), self.assertRaisesRegex(RuntimeError, 'clearance did not arrive'):
            cashier.prepare(api, FIXTURES, NOW)

    def test_outside_clinic_hours_touches_nothing(self):
        api = FakeApi()
        result = cashier.prepare(api, {'historyReady': False, 'sessions': []}, NOW)
        self.assertFalse(result['ready'])
        self.assertEqual(api.calls, [])


if __name__ == '__main__':
    unittest.main()
