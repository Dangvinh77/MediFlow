import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    'rehearse_snapshot', Path(__file__).resolve().parents[1] / 'rehearse_snapshot.py')
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class RehearsalBindingTest(unittest.TestCase):
    def test_compose_pharmacy_store_maps_to_restored_snapshot(self):
        self.assertEqual(runner.snapshot_environment_value(
            'jdbc:postgresql://postgres:5432/mediflow_pharmacy_docker', '61234'),
            'jdbc:postgresql://127.0.0.1:61234/mediflow_pharmacy')
        self.assertEqual(runner.snapshot_environment_value(
            'jdbc:postgresql://postgres:5432/mediflow_clinical', '61234'),
            'jdbc:postgresql://127.0.0.1:61234/mediflow_clinical')

    def test_lan_flag_is_opt_in(self):
        self.assertFalse(runner.parse_options([]).lan)
        self.assertTrue(runner.parse_options(['--lan']).lan)

    def test_only_gateway_binds_all_interfaces_in_lan_mode(self):
        self.assertEqual(runner.server_bind_address('gateway', True), '0.0.0.0')
        for name in runner.NAMES[:-1]:
            with self.subTest(name=name):
                self.assertEqual(runner.server_bind_address(name, True), '127.0.0.1')
        self.assertEqual(runner.server_bind_address('gateway', False), '127.0.0.1')


if __name__ == '__main__':
    unittest.main()
