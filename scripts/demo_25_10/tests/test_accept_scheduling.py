from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import accept_scheduling


class AcceptanceLoginTest(unittest.TestCase):
    def test_every_role_logs_in_with_its_seed_email(self):
        self.assertEqual(dict(accept_scheduling.ACCEPTANCE_LOGINS), {
            'admin': 'demo.staff01@example.test', 'nurse': 'demo.staff04@example.test',
            'doctor': 'demo.staff03@example.test', 'cashier': 'demo.staff19@example.test',
            'patient': 'demo.patient01@example.test', 'other': 'demo.patient02@example.test',
            'lab': 'demo.staff15@example.test'})


if __name__ == '__main__':
    unittest.main()
