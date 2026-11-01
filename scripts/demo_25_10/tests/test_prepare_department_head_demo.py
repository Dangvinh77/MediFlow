import datetime as dt
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("prepare_department_head_demo", Path(__file__).resolve().parents[1] / "prepare_department_head_demo.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class Api:
    def __init__(self, head=None):
        self.claims, self.state, self.calls = {}, {}, []
        self.head = head
    def login(self, role, username):
        self.claims[role] = {"staffId": "doctor-id"}
    def call(self, role, method, path, body=None, expected=(200,), stage=None):
        self.calls.append((role, method, path, body, stage))
        if path == "/api/v1/org/staff/doctor-id":
            return {"staffId": "doctor-id", "departmentId": "dept-id", "fullName": "Demo doctor"}
        if path.endswith("head-appointments"):
            if method == "PUT": self.head = "doctor-id"
            return {"departmentId": "dept-id", "revision": 1 if self.head else 0, "staffId": self.head, "appointmentId": "head-id" if self.head else None}
        if path == "/api/v1/org/me/capabilities":
            return {"role": "DOCTOR", "canPracticeAsDoctor": True, "managementDepartments": [{"departmentId": "dept-id"}] if self.head == "doctor-id" else []}
        if path.startswith("/api/v2/org/consultation-rooms?"):
            return {"content": [{"roomId": "room-id"}]}
        if path == "/api/v2/doctor-work-shifts": return {**body, "shiftId": "shift-id", "revision": 1, "status": "ACTIVE"}
        if path.endswith("/consultation-sessions"): return {**body, "sessionId": "session-id"}
        raise AssertionError((method, path))


class DepartmentHeadDemoTest(unittest.TestCase):
    def test_uses_public_admin_appointment_and_doctor_shift_then_explicit_session(self):
        api = Api()
        result = module.prepare(api, dt.datetime(2026, 10, 31, 18, tzinfo=dt.timezone(dt.timedelta(hours=7))))
        self.assertEqual(result["shift"]["shiftDate"], "2026-11-02")
        writes = [(role, path) for role, method, path, _, _ in api.calls if method in ("POST", "PUT")]
        self.assertEqual(writes, [("admin", "/api/v2/org/departments/dept-id/head-appointments"),
                                  ("doctor", "/api/v2/doctor-work-shifts"),
                                  ("doctor", "/api/v2/doctor-work-shifts/shift-id/consultation-sessions")])
        self.assertTrue(result["capabilities"]["canPracticeAsDoctor"])
        self.assertEqual(result["session"]["capacity"], 12)
        for row in (result["shift"], result["session"]):
            self.assertRegex(row["startTime"], r"^\d{2}:\d{2}$")
            self.assertRegex(row["endTime"], r"^\d{2}:\d{2}$")
    def test_refuses_to_replace_a_different_demo_head(self):
        api = Api("someone-else")
        with self.assertRaisesRegex(RuntimeError, "already has"):
            module.prepare(api, dt.datetime.now(dt.timezone.utc))
        self.assertFalse(any(method in ("POST", "PUT") for _, method, _, _, _ in api.calls))


if __name__ == "__main__": unittest.main()
