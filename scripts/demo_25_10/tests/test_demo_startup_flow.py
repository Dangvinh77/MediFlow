"""Exercise the real runner orchestration with all Docker/JVM/network operations replaced."""
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location('rehearse_flow', Path(__file__).resolve().parents[1] / 'rehearse_snapshot.py')
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class StartupFlowTest(unittest.TestCase):
    def run_demo(self, persistent=True, reuse=True, fail=False, prepare=False, report=False, stop_initializing=False):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        here = root / 'scripts/demo_25_10'
        for name in runner.NAMES:
            target = root / 'backend' / name / 'target'
            target.mkdir(parents=True)
            (target / (name + '-0.0.1-SNAPSHOT.jar')).write_bytes(b'jar')
        workspace = Mock()
        workspace.open.return_value = {'pg': 'owned-pg', 'rabbit': 'owned-rabbit'} if reuse else None
        lock = Mock()
        runtime_module = SimpleNamespace(Workspace=Mock(return_value=workspace), WorkspaceLock=Mock(return_value=lock))
        calls = []
        processes = []
        launch_environments = []
        def command(*args, **kwargs):
            calls.append(args)
            if args[:2] == ('docker', 'compose'):
                value = json.dumps({'services': {name: {} for name in runner.NAMES}})
            elif args[:2] == ('docker', 'port'):
                value = '127.0.0.1:60123'
            else:
                value = ''
            return SimpleNamespace(stdout=value, stderr='', returncode=0)
        def popen(*args, **kwargs):
            if fail and len(processes) == 3:
                raise RuntimeError('fixture JVM launch failure')
            process = Mock(pid=41000 + len(processes))
            process.poll.return_value = None
            processes.append(process)
            launch_environments.append(kwargs['env'])
            return process
        def initialize(options, evidence, containers, pg, rabbit, state):
            if stop_initializing:
                manifest = json.loads((here / 'evidence/active-rehearsal.local.json').read_text())
                self.assertEqual(manifest['evidence'], str(evidence))
                self.assertEqual(manifest['runnerPid'], runner.os.getpid())
                (evidence / 'stop').touch()
                raise InterruptedError('fixture stop during restore')
            containers.extend([pg, rabbit])
            return '60123', '60124'
        ui = SimpleNamespace(DemoApi=Mock(), prepare=Mock(return_value={}), write_fixtures=Mock(), ZONE=runner.dt.timezone.utc)
        calendar = SimpleNamespace(prepare=Mock(), verify=Mock(), write_fixtures=Mock())
        report_demo = SimpleNamespace(prepare=Mock(return_value={
            'availableSyntheticPeriods': 2, 'accepted': True, 'acceptanceAddedThisRun': True}))
        if prepare:
            evidence = here / 'evidence/workspace'
            evidence.mkdir(parents=True)
            (evidence / 'calendar-fixtures.local.json').write_text('{"cached": true}')
            (evidence / 'ui-demo-journal.local.json').write_text('{"saved": true}')
        if report:
            (here / 'evidence/workspace').mkdir(parents=True, exist_ok=True)
        arguments = ['--persistent'] if persistent else []
        if prepare:
            arguments += ['--scheduling', '--prepare-ui-demo']
        if report:
            arguments += ['--scheduling', '--prepare-report-demo']
        with patch.object(runner, 'ROOT', root), patch.object(runner, 'HERE', here), \
                patch.object(runner, 'parse_options', return_value=runner.parse_options(arguments)), \
                patch.object(runner, 'command', side_effect=command), \
                patch.object(runner.socket, 'socket'), patch.object(runner, 'await_broker'), \
                patch.object(runner, 'get_json', return_value={'status': 'UP'}), \
                patch.object(runner, 'initialize_infrastructure', side_effect=initialize) as init, \
                patch.object(runner.subprocess, 'Popen', side_effect=popen), \
                patch.dict('sys.modules', {'demo_runtime': runtime_module, 'prepare_ui_demo': ui,
                                          'prepare_demo_calendar': calendar,
                                          'prepare_report_demo': report_demo}):
            if fail:
                with self.assertRaisesRegex(RuntimeError, 'JVM launch failure'):
                    runner.main()
            else:
                runner.main()
        return SimpleNamespace(root=root, here=here, calls=calls, processes=processes,
                               launch_environments=launch_environments, workspace=workspace, lock=lock,
                               initialize=init, ui=ui, calendar=calendar, report_demo=report_demo)

    def test_warm_start_skips_restore_and_preserves_both_containers(self):
        result = self.run_demo()
        result.initialize.assert_not_called()
        self.assertEqual(len(result.processes), 8)
        for process in result.processes:
            process.terminate.assert_called_once()
        self.assertFalse(any(call[:2] == ('docker', 'rm') for call in result.calls))
        result.lock.release.assert_called_once()

    def test_stop_during_first_restore_has_current_manifest_and_launches_no_jvm(self):
        result = self.run_demo(reuse=False, stop_initializing=True)
        self.assertEqual(result.processes, [])
        result.lock.release.assert_called_once()
        self.assertIn('STOPPED', (result.here / 'evidence/workspace/result.txt').read_text())

    def test_fresh_persistent_start_initializes_once_and_retains_data(self):
        result = self.run_demo(reuse=False)
        result.workspace.begin.assert_called_once()
        result.initialize.assert_called_once()
        self.assertFalse(any(call[:2] == ('docker', 'rm') for call in result.calls))

    def test_launch_failure_terminates_all_started_jvms_but_keeps_data(self):
        result = self.run_demo(fail=True)
        self.assertEqual(len(result.processes), 3)
        for process in result.processes:
            process.terminate.assert_called_once()
        self.assertFalse(any(call[:2] == ('docker', 'rm') for call in result.calls))
        result.lock.release.assert_called_once()

    def test_disposable_mode_still_removes_its_own_containers(self):
        result = self.run_demo(persistent=False, reuse=False)
        self.assertEqual(sum(call[:2] == ('docker', 'rm') for call in result.calls), 2)

    def test_warm_preparation_reuses_journal_and_verifies_calendar_without_replaying_writes(self):
        result = self.run_demo(prepare=True)
        result.calendar.prepare.assert_not_called()
        result.calendar.verify.assert_called_once()
        self.assertEqual(json.loads((result.here / 'evidence/workspace/ui-demo-journal.local.json').read_text()), {'saved': True})
        self.assertEqual(result.ui.DemoApi.call_args_list[0].args[1], result.here / 'evidence/workspace/ui-demo-journal.local.json')

    def test_report_demo_acceptance_gate_is_open_only_for_seed_then_support_restarts_closed(self):
        result = self.run_demo(report=True)
        result.report_demo.prepare.assert_called_once()
        self.assertTrue(result.report_demo.prepare.call_args.kwargs['accept'])
        self.assertEqual(result.launch_environments[6]['MEDIFLOW_REPORT_OUTPATIENT_COVERAGE_ACCEPTANCE_ENABLED'], 'true')
        self.assertEqual(result.launch_environments[-1]['MEDIFLOW_REPORT_OUTPATIENT_COVERAGE_ACCEPTANCE_ENABLED'], 'false')
        self.assertEqual(len(result.processes), 9)
        result.processes[6].terminate.assert_called_once()
        self.assertEqual(result.report_demo.prepare.call_args.args[1], result.here / 'evidence/workspace')


class BrokerUiTest(unittest.TestCase):
    def broker_run_args(self, *arguments):
        calls = []
        run = lambda *args, **kwargs: calls.append(args) or SimpleNamespace(stdout='id\n', returncode=0)
        runner.start_broker(run, 'mediflow-rehearsal-x-rabbit', [], runner.parse_options(list(arguments)))
        return calls[0]

    def test_broker_uses_management_image_with_loopback_ui_by_default(self):
        args = self.broker_run_args()
        self.assertIn('rabbitmq:3.13-management-alpine', args)
        self.assertIn('127.0.0.1:28672:15672', args)
        self.assertIn('127.0.0.1::5672', args)

    def test_lan_ui_is_opt_in_and_amqp_stays_loopback(self):
        args = self.broker_run_args('--lan', '--broker-ui-lan')
        self.assertIn('0.0.0.0:28672:15672', args)
        self.assertIn('127.0.0.1::5672', args)
        with self.assertRaises(SystemExit):
            runner.parse_options(['--broker-ui-lan'])

    def test_retained_plain_broker_is_replaced_but_ui_broker_is_kept(self):
        workspace = Mock(docker_labels=['--label', 'a=b'], state={'containers': {'rabbit': {'id': 'old-id'}}})
        for has_ui, expected_rm in ((True, 0), (False, 1)):
            calls = []
            def run(*args, **kwargs):
                calls.append(args)
                missing = args[:2] == ('docker', 'port') and not has_ui
                return SimpleNamespace(stdout='' if missing else '127.0.0.1:28672\n', returncode=1 if missing else 0)
            with patch.object(runner, 'await_broker'):
                runner.ensure_broker_ui(run, workspace, 'rabbit', runner.parse_options([]), None)
            self.assertEqual(sum(call[:2] == ('docker', 'rm') for call in calls), expected_rm)
        workspace.track.assert_called_once()


if __name__ == '__main__':
    unittest.main()
