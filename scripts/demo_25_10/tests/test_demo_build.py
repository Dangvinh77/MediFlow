import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('demo_build', Path(__file__).resolve().parents[1] / 'demo_build.py')


class BuildCacheTest(unittest.TestCase):
    def setUp(self):
        self.module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(self.module)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'pom.xml').write_text('parent')
        for name in self.module.NAMES:
            path = self.root / 'backend' / name
            (path / 'target').mkdir(parents=True)
            (path / 'src/main').mkdir(parents=True)
            (path / 'pom.xml').write_text('module')
            (path / 'src/main/input.txt').write_text('input')
            (path / 'target' / (name + '-0.0.1-SNAPSHOT.jar')).write_bytes(b'jar')

    def test_unknown_build_and_changed_source_need_build(self):
        self.assertFalse(self.module.is_current(self.root))
        self.module.record(self.root)
        self.assertTrue(self.module.is_current(self.root))
        (self.root / 'backend/core-service/src/main/input.txt').write_text('changed')
        self.assertFalse(self.module.is_current(self.root))

    def test_replaced_or_missing_jar_needs_build(self):
        self.module.record(self.root)
        jar = self.root / 'backend/gateway/target/gateway-0.0.1-SNAPSHOT.jar'
        jar.write_bytes(b'other')
        self.assertFalse(self.module.is_current(self.root))
        jar.unlink()
        self.assertFalse(self.module.is_current(self.root))

    def test_source_change_during_build_cannot_be_marked_current(self):
        before = self.module.fingerprint(self.root)
        (self.root / 'pom.xml').write_text('changed during build')
        with self.assertRaisesRegex(RuntimeError, 'Source changed'):
            self.module.record(self.root, expected_inputs=before)
        self.assertFalse(self.module.is_current(self.root))

    def test_common_change_invalidates_all_and_corrupt_cache_is_safe(self):
        self.module.record(self.root)
        common = self.root / 'backend/common/src/main/java/Shared.java'
        common.parent.mkdir(parents=True)
        common.write_text('change')
        self.assertFalse(self.module.is_current(self.root))
        self.module.record(self.root)
        self.module.cache_path(self.root).write_text('{broken')
        self.assertFalse(self.module.is_current(self.root))


if __name__ == '__main__':
    unittest.main()
