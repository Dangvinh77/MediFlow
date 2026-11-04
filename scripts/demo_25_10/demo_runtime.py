"""Own only the persistent rehearsal containers created for this checkout."""
import hashlib
import json
import os
import re
import uuid


class WorkspaceLock:
    """OS-owned lock releases automatically if the runner is killed."""
    def __init__(self, path):
        self.path, self.stream = path, None

    def acquire(self):
        self.stream = self.path.open('a+b')
        if self.stream.tell() == 0:
            self.stream.write(b'0')
            self.stream.flush()
        self.stream.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(self.stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            self.stream.close()
            self.stream = None
            raise RuntimeError('The retained demo is already starting/running. Stop its backend first.') from error

    def release(self):
        if self.stream:
            self.stream.close()
            self.stream = None


class Workspace:
    def __init__(self, root, evidence, command, scheduling):
        self.root, self.evidence, self.command = root.resolve(), evidence.resolve(), command
        self.path = self.evidence / 'infrastructure.local.json'
        self.owner = hashlib.sha256(str(self.root).casefold().encode()).hexdigest()
        inputs = hashlib.sha256(str(scheduling).encode())
        for name in ('MediFlow_Demo_Database_2026-10-25.sql', 'seed_outpatient_scheduling.sql'):
            path = root / 'scripts/demo_25_10' / name
            if path.exists():
                inputs.update(path.read_bytes())
        self.signature = inputs.hexdigest()
        self.state = None

    @property
    def labels(self):
        return {'io.mediflow.demo.workspace': self.owner, 'io.mediflow.demo.session': self.state['session']}

    @property
    def docker_labels(self):
        return [item for key, value in self.labels.items() for item in ('--label', key + '=' + value)]

    def save(self):
        self.evidence.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix('.tmp')
        temporary.write_text(json.dumps(self.state, indent=2), encoding='utf-8')
        temporary.replace(self.path)

    def begin(self):
        self.state = {'owner': self.owner, 'session': uuid.uuid4().hex, 'signature': self.signature,
                      'ready': False, 'containers': {}}
        self.save()

    def track(self, kind, name, identity):
        self.state['containers'][kind] = {'name': name, 'id': identity.strip()}
        self.save()

    def complete(self):
        self.state['ready'] = True
        self.save()

    def open(self, reset=False):
        if not self.path.exists():
            return None
        self.state = json.loads(self.path.read_text(encoding='utf-8'))
        if (self.state.get('owner') != self.owner
                or not re.fullmatch('[0-9a-f]{32}', self.state.get('session', ''))
                or not isinstance(self.state.get('containers'), dict)
                or set(self.state['containers']) - {'pg', 'rabbit'}):
            raise RuntimeError('Persistent demo ownership metadata is invalid; no container was changed')
        verified = []
        missing = False
        for row in self.state['containers'].values():
            identity = row['id']
            # Inspect by immutable ID; a foreign container with the same name is never adopted.
            result = self.command('docker', 'inspect', identity, check=False)
            if result.returncode:
                message = result.stdout + getattr(result, 'stderr', '')
                if 'No such object:' in message or 'No such container:' in message:
                    missing = True
                    continue
                raise RuntimeError('Cannot inspect persistent demo; no container was changed')
            container = json.loads(result.stdout)[0]
            labels = container.get('Config', {}).get('Labels') or {}
            if (container['Id'] != identity or container['Name'] != '/' + row['name']
                    or not row['name'].startswith('mediflow-rehearsal-')
                    or any(labels.get(key) != value for key, value in self.labels.items())):
                raise RuntimeError('Persistent demo ownership check failed; no container was changed')
            verified.append(container)
        if reset:
            # Verify ALL identities before the first removal; never recursively delete evidence.
            for container in verified:
                self.command('docker', 'rm', '-f', container['Id'])
            for name in ('ui-demo-journal.local.json', 'calendar-journal.local.json',
                         'ui-demo-fixtures.local.json', 'calendar-fixtures.local.json',
                         'DEMO-DATA.local.md', 'DEMO-CALENDAR.local.md'):
                (self.evidence / name).unlink(missing_ok=True)
            self.path.unlink()
            return None
        if (missing or not self.state.get('ready') or set(self.state['containers']) != {'pg', 'rabbit'}
                or self.state.get('signature') != self.signature):
            raise RuntimeError('Persistent demo is incomplete or seed/configuration changed. Run start-backend.bat -ResetData')
        for container in verified:
            if not container['State']['Running']:
                self.command('docker', 'start', container['Id'])
        return {kind: row['name'] for kind, row in self.state['containers'].items()}
