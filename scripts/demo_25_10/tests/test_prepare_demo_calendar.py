import datetime as dt
import importlib.util
from pathlib import Path
import tempfile
import unittest
import urllib.parse

SPEC = importlib.util.spec_from_file_location('prepare_demo_calendar', Path(__file__).resolve().parents[1] / 'prepare_demo_calendar.py')
calendar = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(calendar)


class Api:
    def __init__(self):
        self.state, self.rows, self.writes = {}, {}, 0
        self.logins = []
        self.departments = [
            {'departmentId': 'clinical', 'departmentName': 'Khoa Nội', 'departmentType': 'CLINICAL', 'active': True},
            {'departmentId': 'lab', 'departmentName': 'Khoa Xét nghiệm', 'departmentType': 'PARACLINICAL', 'active': True},
            {'departmentId': 'pharmacy', 'departmentName': 'Khoa Dược', 'departmentType': 'PARACLINICAL', 'active': True},
            {'departmentId': 'office', 'departmentName': 'Phòng Hành chính', 'departmentType': 'ADMINISTRATIVE', 'active': True},
        ]

    def login(self, role, username):
        self.logins.append((role, username))

    def call(self, role, method, path, body=None, expected=(200,), stage=None, **kwargs):
        if method == 'POST':
            if stage in self.state:
                self.assert_body(stage, body, path)
                return self.state[stage]['result']
            self.writes += 1
            result = {**body, 'active': True, 'isActive': True, 'revision': 1, 'status': 'ACTIVE'}
            if path.endswith('/staff'):
                result['staffId'] = str(self.writes)
                kind = 'staff'
            elif path.endswith('/accounts'):
                result['accountId'] = str(self.writes)
                kind = 'accounts'
            elif path.endswith('/doctor-work-shifts'):
                result['shiftId'] = str(self.writes)
                kind = 'shifts'
            else:
                result.update(sessionId=str(self.writes), status='OPEN', availableCount=body['capacity'])
                kind = 'sessions'
            self.rows.setdefault(kind, []).append(result)
            self.state[stage] = {'body': body, 'path': path, 'result': result}
            return result
        parsed = urllib.parse.urlsplit(path)
        query = dict(urllib.parse.parse_qsl(parsed.query))
        if parsed.path.endswith('/departments'):
            return self.departments
        if parsed.path.endswith('/consultation-rooms'):
            return {'content': [{'roomId': 'room', 'roomName': calendar.ROOM_NAME}], 'totalPages': 1}
        kind = 'staff' if parsed.path.endswith('/staff') else 'accounts' if parsed.path.endswith('/accounts') else 'sessions'
        rows = self.rows.get(kind, [])
        rows = [r for r in rows if not query.get('departmentId') or r['departmentId'] == query['departmentId']]
        rows = [r for r in rows if query.get('activeOnly') != 'true' or r.get('active', True)]
        rows = [r for r in rows if not query.get('fromDate') or query['fromDate'] <= r['sessionDate'] <= query['toDate']]
        # Force pagination even on small fixtures; readers must fetch beyond page 0.
        size, page = 7, int(query.get('page', 0))
        return {'content': rows[page * size:(page + 1) * size], 'totalPages': (len(rows) + size - 1) // size}

    def assert_body(self, stage, body, path):
        assert self.state[stage]['body'] == body and self.state[stage]['path'] == path


class CalendarTest(unittest.TestCase):
    def test_inclusive_92_days_continuous_two_shifts_and_rotation(self):
        days = list(calendar.dates(calendar.START, calendar.END))
        self.assertEqual(len(days), 92)
        self.assertEqual(days[0], dt.date(2026, 11, 1))
        self.assertEqual(days[-1], dt.date(2027, 1, 31))
        self.assertEqual(calendar.SLOTS, (('07:00', '12:00'), ('12:00', '17:00')))

    def test_every_department_staffed_clinical_only_scheduled_and_rerun_has_no_duplicates(self):
        api = Api()
        result = calendar.prepare(api)
        self.assertEqual(api.logins, [('admin', 'demo.staff01@example.test')])
        self.assertEqual(len(api.rows['staff']), 40)
        self.assertFalse(api.rows.get('accounts'))
        self.assertEqual(len(api.rows['shifts']), 184)
        self.assertEqual(len(api.rows['sessions']), 184)
        clinical = api.rows['staff'][:10]
        self.assertEqual([r['jobTitle'] for r in clinical].count('DOCTOR'), 6)
        self.assertEqual([r['jobTitle'] for r in clinical].count('NURSE'), 4)
        self.assertTrue(all(r['departmentId'] == 'clinical' for r in api.rows['sessions']))
        self.assertEqual(len({r['doctorId'] for r in api.rows['sessions']}), 6)
        self.assertEqual(set(r['jobTitle'] for r in api.rows['staff'][20:30]), {'PHARMACIST'})
        self.assertEqual(set(r['jobTitle'] for r in api.rows['staff'][30:40]), {'ADMINISTRATIVE'})
        self.assertEqual(len({r['identityNumber'] for r in api.rows['staff']}), 40)
        self.assertEqual(result['verifiedSessions'], 184)
        writes = api.writes
        calendar.prepare(api)
        self.assertEqual(api.writes, writes)

    def test_fixture_guide_never_publishes_generated_or_seed_passwords(self):
        with tempfile.TemporaryDirectory() as folder:
            result = calendar.prepare(Api())
            calendar.write_fixtures(result, Path(folder))
            guide = (Path(folder) / 'DEMO-CALENDAR.local.md').read_text(encoding='utf-8')
        self.assertNotIn('MediFlowDemo@2026', guide)
        self.assertNotIn('Mật khẩu ban đầu', guide)
        self.assertIn('không tạo tài khoản', guide.casefold())

    def test_replay_detects_changed_profession_or_revoked_account(self):
        for target, field, value, message in [('staff', 'jobTitle', 'NURSE', 'Staffing'),
                                               ('staff', 'active', False, 'Staffing')]:
            with self.subTest(target=target):
                api = Api()
                # API search and journal are independent snapshots, as on the real Gateway.
                result = calendar.prepare(api)
                api.rows[target][0] = {**api.rows[target][0], field: value}
                with self.assertRaisesRegex(RuntimeError, message):
                    calendar.verify(api, result)

    def test_verification_detects_cancelled_session_instead_of_reopening_it(self):
        api = Api()
        calendar.prepare(api)
        api.rows['sessions'][99]['status'] = 'CANCELLED'
        with self.assertRaisesRegex(RuntimeError, 'coverage|Coverage'):
            calendar.prepare(api)

    def test_missing_bootstrap_room_fails_before_clinical_writes(self):
        api = Api()
        original = api.call
        def call(role, method, path, *args, **kwargs):
            if '/consultation-rooms?' in path:
                return {'content': [], 'totalPages': 0}
            return original(role, method, path, *args, **kwargs)
        api.call = call
        with self.assertRaisesRegex(RuntimeError, 'room|Room'):
            calendar.prepare(api)
        self.assertFalse(api.rows.get('shifts'))


if __name__ == '__main__':
    unittest.main()
