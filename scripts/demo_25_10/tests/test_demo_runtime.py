import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

SPEC = importlib.util.spec_from_file_location('demo_runtime', Path(__file__).resolve().parents[1] / 'demo_runtime.py')


class PersistentRuntimeTest(unittest.TestCase):
    def setUp(self):
        self.module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(self.module)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)
        self.calls = []
        self.containers = {}

        def command(*args, **kwargs):
            self.calls.append(args)
            if args[:2] == ('docker', 'inspect'):
                return SimpleNamespace(returncode=0, stdout=json.dumps([self.containers[args[2]]]))
            return SimpleNamespace(returncode=0, stdout='')
        self.workspace = self.module.Workspace(self.path, self.path, command, True)

    def track(self, kind):
        identity = 'id-' + kind
        name = 'mediflow-rehearsal-test-' + kind
        self.containers[identity] = {'Id': identity, 'Name': '/' + name,
                                     'Config': {'Labels': self.workspace.labels}, 'State': {'Running': True}}
        self.workspace.track(kind, name, identity)

    def test_reuse_retains_identity_and_never_removes_containers(self):
        self.workspace.begin()
        self.track('pg')
        self.track('rabbit')
        self.workspace.complete()
        other = self.module.Workspace(self.path, self.path, self.workspace.command, True)
        self.assertEqual(other.open(), {'pg': 'mediflow-rehearsal-test-pg', 'rabbit': 'mediflow-rehearsal-test-rabbit'})
        self.assertFalse(any('rm' in call for call in self.calls))

    def test_workspace_lock_prevents_second_runner_and_releases_after_stop(self):
        first = self.module.WorkspaceLock(self.path / 'runner.lock')
        second = self.module.WorkspaceLock(self.path / 'runner.lock')
        first.acquire()
        try:
            with self.assertRaisesRegex(RuntimeError, 'already starting/running'):
                second.acquire()
        finally:
            first.release()
        second.acquire()
        second.release()

    def test_stopped_owned_infrastructure_is_started_without_restore(self):
        self.workspace.begin()
        self.track('pg')
        self.track('rabbit')
        self.workspace.complete()
        self.containers['id-pg']['State']['Running'] = False
        self.workspace.open()
        self.assertIn(('docker', 'start', 'id-pg'), self.calls)
        self.assertFalse(any('rm' in call for call in self.calls))

    def test_incomplete_initialization_requires_explicit_reset(self):
        self.workspace.begin()
        self.track('pg')
        with self.assertRaisesRegex(RuntimeError, 'ResetData'):
            self.workspace.open()
        self.workspace.open(reset=True)
        self.assertIn(('docker', 'rm', '-f', 'id-pg'), self.calls)
        self.assertFalse(self.workspace.path.exists())

    def test_foreign_container_prevents_any_reset(self):
        self.workspace.begin()
        self.track('pg')
        self.track('rabbit')
        self.containers['id-rabbit']['Config'] = {'Labels': {}}
        with self.assertRaisesRegex(RuntimeError, 'ownership'):
            self.workspace.open(reset=True)
        self.assertFalse(any('rm' in call for call in self.calls))

    def test_input_change_requires_reset_instead_of_reusing_old_seed(self):
        self.workspace.begin()
        self.track('pg')
        self.track('rabbit')
        self.workspace.complete()
        other = self.module.Workspace(self.path, self.path, self.workspace.command, False)
        with self.assertRaisesRegex(RuntimeError, 'ResetData'):
            other.open()

    def test_reset_does_not_remove_journals_until_containers_are_verified(self):
        self.workspace.begin()
        self.track('pg')
        journal = self.path / 'ui-demo-journal.local.json'
        journal.write_text('journal')
        self.containers['id-pg']['Name'] = '/foreign'
        with self.assertRaises(RuntimeError):
            self.workspace.open(reset=True)
        self.assertEqual(journal.read_text(), 'journal')

    def test_removed_container_requires_reset_and_is_not_replaced_by_name(self):
        self.workspace.begin()
        self.track('pg')
        self.track('rabbit')
        self.workspace.complete()
        original = self.workspace.command
        def command(*args, **kwargs):
            if args == ('docker', 'inspect', 'id-pg'):
                return SimpleNamespace(returncode=1, stdout='', stderr='Error: No such object: id-pg')
            return original(*args, **kwargs)
        self.workspace.command = command
        with self.assertRaisesRegex(RuntimeError, 'ResetData'):
            self.workspace.open()
        self.workspace.open(reset=True)
        self.assertIn(('docker', 'rm', '-f', 'id-rabbit'), self.calls)
        self.assertFalse(self.workspace.path.exists())

    def test_daemon_error_is_not_treated_as_a_missing_container(self):
        self.workspace.begin()
        self.track('pg')
        self.workspace.command = lambda *args, **kwargs: SimpleNamespace(returncode=1, stdout='', stderr='Cannot connect to Docker daemon')
        with self.assertRaisesRegex(RuntimeError, 'Cannot inspect'):
            self.workspace.open(reset=True)
        self.assertTrue(self.workspace.path.exists())


if __name__ == '__main__':
    unittest.main()
