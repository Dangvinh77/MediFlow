import datetime as dt
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import prepare_staged_demo as staged
from test_prepare_cashier_demo import FIXTURES, NOW, FakeApi as CashierFakeApi

NAMES = {cccd: 'Benh nhan ' + cccd[-2:] for cccd in
         list(staged.CASES.values()) + list(staged.RESERVED.values()) + [staged.EXAM_CHARGE_CCCD]}


class FakeApi(CashierFakeApi):
    def __init__(self):
        super().__init__()
        self.payment_confirmed_after = 0

    def call(self, role, method, path, body=None, expected=(200,), stage=None):
        if path.startswith('/api/v1/patients?'):
            self.calls.append((role, method, path, body))
            cccd = dict(part.split('=') for part in path.split('?')[1].split('&'))['keyword']
            return {'content': [{'maBenhNhan': 'p' + cccd[-2:], 'soCmnd': cccd, 'hoTen': NAMES[cccd]},
                                {'maBenhNhan': 'other', 'soCmnd': cccd + '9', 'hoTen': 'Khac'}]}
        if method == 'GET' and path.startswith('/api/v1/pharmacy/prescriptions/'):
            self.calls.append((role, method, path, body))
            if self.payment_confirmed_after:
                self.payment_confirmed_after -= 1
                return {'paymentConfirmed': False}
            return {'paymentConfirmed': True}
        if method == 'GET' and path.startswith('/api/v2/outpatient-registrations/'):
            self.calls.append((role, method, path, body))
            return {'status': 'CONFIRMED', 'reservationExpiresAt': '2026-11-07T02:15:00Z'}
        return super().call(role, method, path, body, expected, stage)

    def patients_with(self, suffix):
        return sorted({row[2].split('/')[-2] if row[2].endswith(suffix) else None for row in self.calls} - {None})


class StagedDemoTest(unittest.TestCase):
    def prepare(self, api=None):
        api = api or FakeApi()
        with patch.object(staged.time, 'sleep'):
            return api, staged.prepare(api, FIXTURES, NOW)

    def test_each_case_gets_its_own_seeded_patient(self):
        api, result = self.prepare()
        self.assertTrue(result['ready'], result['failed'])
        registered = [row[3]['patientId'] for row in api.posts('/api/v2/outpatient-registrations')]
        self.assertEqual(registered, ['p03', 'p04', 'p05', 'p06', 'p07'])
        self.assertEqual(len(set(registered)), len(registered))
        for reserved in ('p01', 'p09', 'p10', 'p08'):
            self.assertNotIn(reserved, registered)

    def test_exam_ready_case_stops_before_the_exam_starts(self):
        api, result = self.prepare()
        self.assertEqual(result['cases']['vinh_exam_ready']['registrationId'], 'visit-p03')
        started = [row[2] for row in api.calls if row[2].endswith('/start-exam')]
        self.assertNotIn('/api/v1/appointments/visit-p03/start-exam', started)
        self.assertIn('/api/v1/appointments/visit-p04/start-exam', started)

    def test_paid_lab_waits_for_the_lab_tech_and_released_lab_is_done(self):
        api, result = self.prepare()
        self.assertEqual(len(api.posts('/lab-payments')), 2)
        self.assertEqual(result['cases']['vinh_lab_paid']['labStatus'], 'READY')
        self.assertEqual(result['cases']['vinh_lab_done']['labStatus'], 'COMPLETED')
        releases = [row[2] for row in api.calls if row[2].endswith('/report/release')]
        self.assertEqual(releases, ['/api/v2/lab/test-p05/report/release'])
        starts = [row[2] for row in api.calls if row[2].endswith('/start') and '/lab/' in row[2]]
        self.assertEqual(starts, ['/api/v1/lab/test-p05/start'])

    def test_no_record_is_completed_so_presenters_close_them_on_stage(self):
        api, _ = self.prepare()
        self.assertEqual([row for row in api.calls if row[2].endswith('/complete')], [])

    def test_prescription_case_is_paid_but_not_dispensed(self):
        api = FakeApi()
        api.payment_confirmed_after = 2
        api, result = self.prepare(api)
        self.assertEqual(result['cases']['huy_dispense']['prescriptionId'], 'rx-p07')
        self.assertEqual(len(api.posts('/payments')), 1)
        self.assertEqual([row for row in api.calls if row[2].endswith('/dispense')], [])
        self.assertEqual([row[3]['patientId'] for row in api.posts('/api/v1/pharmacy/prescriptions')], ['p07'])

    def test_one_failed_case_is_reported_and_the_others_still_run(self):
        api = FakeApi()
        original = staged.STEPS['vinh_lab_paid']
        with patch.dict(staged.STEPS, {'vinh_lab_paid': lambda *a: (_ for _ in ()).throw(RuntimeError('lab down'))}):
            api, result = self.prepare(api)
        self.assertFalse(result['ready'])
        self.assertEqual(result['failed']['vinh_lab_paid']['error'], 'lab down')
        self.assertIn('huy_dispense', result['cases'])
        self.assertIs(staged.STEPS['vinh_lab_paid'], original)

    def test_outside_clinic_hours_touches_nothing(self):
        api = FakeApi()
        result = staged.prepare(api, {'historyReady': False, 'sessions': []}, NOW)
        self.assertFalse(result['ready'])
        self.assertEqual(api.calls, [])

    def test_exam_charge_is_registered_and_left_unpaid(self):
        api = FakeApi()
        result = staged.exam_charge_now(api, FIXTURES, NOW)
        self.assertEqual(result['patientId'], 'p08')
        self.assertEqual(len(api.posts('/api/v2/outpatient-registrations')), 1)
        self.assertEqual(api.posts('/payments'), [])

    def test_hand_off_sheet_lists_every_case_and_the_failure(self):
        api = FakeApi()
        with patch.dict(staged.STEPS, {'huy_prescribe': lambda *a: (_ for _ in ()).throw(RuntimeError('boom'))}):
            _, result = self.prepare(api)
        with tempfile.TemporaryDirectory() as folder:
            with patch('builtins.print'):
                staged.write_fixtures(result, Path(folder))
            sheet = (Path(folder) / 'DEMO-STAGED.local.md').read_text(encoding='utf-8')
        self.assertIn('visit-p03', sheet)
        self.assertIn('rx-p07', sheet)
        self.assertIn('**LỖI** | boom', sheet)
        self.assertIn('seed-demo.bat -ExamChargeNow', sheet)


if __name__ == '__main__':
    unittest.main()
