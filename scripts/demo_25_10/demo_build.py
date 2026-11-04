"""Validate packaged demo JARs against their local Maven inputs, without trusting mtimes."""
import argparse
import hashlib
import json
from pathlib import Path

NAMES = ('eureka-server', 'core-service', 'clinical-service', 'lab-service',
         'pharmacy-service', 'billing-service', 'support-service', 'gateway')
ROOT = Path(__file__).resolve().parents[2]


def cache_path(root):
    return root / 'scripts/demo_25_10/evidence/build.local.json'


def fingerprint(root):
    inputs = [root / 'pom.xml']
    for module in (root / 'backend').iterdir():
        if module.is_dir():
            inputs.extend(module.glob('pom.xml'))
            inputs.extend(path for path in (module / 'src/main').rglob('*') if path.is_file())
    inputs.extend(path for path in (root / '.mvn').rglob('*') if path.is_file())
    inputs.extend(path for path in (root / 'backend').glob('pom.xml'))
    result = hashlib.sha256()
    for path in sorted(set(inputs)):
        result.update(path.relative_to(root).as_posix().encode('utf-8') + b'\0')
        result.update(hashlib.sha256(path.read_bytes()).digest())
    return result.hexdigest()


def snapshot(root):
    jars = {}
    for name in NAMES:
        path = root / 'backend' / name / 'target' / (name + '-0.0.1-SNAPSHOT.jar')
        jars[name] = None
        if path.is_file():
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(block)
            jars[name] = digest.hexdigest()
    return {'version': 1, 'inputs': fingerprint(root), 'jars': jars}


def is_current(root=ROOT):
    try:
        saved = json.loads(cache_path(root).read_text(encoding='utf-8'))
        current = snapshot(root)
        return all(current['jars'].values()) and saved == current
    except (OSError, ValueError):
        return False


def record(root=ROOT, expected_inputs=None):
    value = snapshot(root)
    if expected_inputs is not None and value['inputs'] != expected_inputs:
        raise RuntimeError('Source changed during Maven build. Run startup again to rebuild current inputs.')
    if not all(value['jars'].values()):
        raise RuntimeError('Cannot cache build: a demo JAR is missing')
    path = cache_path(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, indent=2), encoding='utf-8')
    temporary.replace(path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('check', 'record', 'fingerprint'))
    parser.add_argument('--expected-inputs')
    options = parser.parse_args()
    if options.action == 'record':
        record(expected_inputs=options.expected_inputs)
    elif options.action == 'fingerprint':
        print(fingerprint(ROOT))
    else:
        raise SystemExit(0 if is_current() else 10)
