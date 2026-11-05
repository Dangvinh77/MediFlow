import importlib.util
from types import SimpleNamespace
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location(
    'rehearse_snapshot', Path(__file__).resolve().parents[1] / 'rehearse_snapshot.py')
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class RehearsalBindingTest(unittest.TestCase):
    def test_retained_broker_old_boot_log_does_not_bypass_live_diagnostic(self):
        pings = []
        def command(*args, **kwargs):
            if 'rabbitmq-diagnostics' in args:
                pings.append(args)
                code = 1 if len(pings) == 1 else 0
                if code and kwargs.get('check', True):
                    raise RuntimeError('broker still booting')
                return SimpleNamespace(returncode=code, stdout='', stderr='')
            return SimpleNamespace(returncode=0, stdout='running' if 'inspect' in args else 'Server startup complete (old boot)', stderr='')
        with patch.object(runner, 'command', side_effect=command), patch.object(runner.time, 'sleep'):
            runner.await_broker('retained-rabbit')
        self.assertEqual(len(pings), 2)

    def test_stop_request_interrupts_readiness_without_retry(self):
        with patch.object(runner.time, 'sleep', side_effect=AssertionError('Stop must not be retried')):
            with self.assertRaises(InterruptedError):
                runner.await_ready(Mock(side_effect=InterruptedError('stopped')))

    def test_parallelism_is_bounded_and_gateway_waits_for_business_services(self):
        events = []
        live = set()
        def launch(name):
            live.add(name)
            events.append(('launch', name))
            self.assertLessEqual(len(live), 2)
            return name
        def wait(name, process):
            self.assertEqual(name, process)
            live.remove(name)
            events.append(('ready', name))
        runner.start_application_batches(launch, wait, 2)
        self.assertEqual(events[:4], [('launch', 'eureka-server'), ('ready', 'eureka-server'),
                                     ('launch', 'core-service'), ('ready', 'core-service')])
        self.assertEqual(events[-2:], [('launch', 'gateway'), ('ready', 'gateway')])
        self.assertEqual(len(events), 16)

    def test_failed_health_check_does_not_launch_later_batches(self):
        launch = Mock(return_value='process')
        with self.assertRaises(RuntimeError):
            runner.start_application_batches(launch, Mock(side_effect=RuntimeError('unhealthy')), 2)
        launch.assert_called_once_with('eureka-server')

    def test_persistent_and_reset_options_are_explicit(self):
        self.assertFalse(runner.parse_options([]).persistent)
        self.assertTrue(runner.parse_options(['--persistent']).persistent)
        with self.assertRaises(SystemExit):
            runner.parse_options(['--reset-data'])
        with self.assertRaises(SystemExit):
            runner.parse_options(['--startup-parallelism', '9'])

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

    def test_report_demo_acceptance_is_an_explicit_scheduling_preparation(self):
        self.assertTrue(runner.parse_options(['--scheduling', '--prepare-report-demo']).prepare_report_demo)
        with self.assertRaises(SystemExit):
            runner.parse_options(['--prepare-report-demo'])

    def test_port_offset_supports_parallel_loopback_rehearsal_without_ui_shortcuts(self):
        options = runner.parse_options(['--port-offset', '10000'])
        self.assertEqual(options.port_offset, 10000)
        with self.assertRaises(SystemExit):
            runner.parse_options(['--port-offset', '10000', '--scheduling', '--prepare-ui-demo'])

    def test_only_gateway_binds_all_interfaces_in_lan_mode(self):
        self.assertEqual(runner.server_bind_address('gateway', True), '0.0.0.0')
        for name in runner.NAMES[:-1]:
            with self.subTest(name=name):
                self.assertEqual(runner.server_bind_address(name, True), '127.0.0.1')
        self.assertEqual(runner.server_bind_address('gateway', False), '127.0.0.1')


if __name__ == '__main__':
    unittest.main()
