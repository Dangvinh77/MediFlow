"""Gateway issuance races and lost-response recovery, disposable fixtures only."""
from concurrent.futures import ThreadPoolExecutor
import http.client
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import socket
import threading
import urllib.request
import uuid


PATH = '/api/v1/org/patient-accounts'


def verify_issuance(call, login, tokens, claims, evidence, gateway):
    staff = call('admin', 'GET', '/api/v1/org/staff/' + claims['nurse']['staffId'])
    second = call('admin', 'POST', '/api/v1/org/staff', {
        'fullName': 'Dieu duong kiem chung concurrent', 'departmentId': staff['departmentId'],
        'jobTitle': 'NURSE', 'identityNumber': str(uuid.uuid4().int)[:12]}, (201,))
    username = 'race.nurse.' + uuid.uuid4().hex[:12]
    account = call('admin', 'POST', '/api/v1/org/accounts', {
        'username': username, 'password': 'RaceNurse@2026', 'staffId': second['staffId'],
        'role': 'NURSE'}, (201,))
    login('nurse2', username, 'RaceNurse@2026')
    assert claims['nurse2']['sub'] == account['accountId']
    assert claims['nurse2']['sub'] != claims['nurse']['sub']
    assert claims['nurse2']['role'] == 'NURSE'

    def patient():
        identity = 'RCV' + uuid.uuid4().hex[:16]
        created = call('nurse', 'POST', '/api/v1/patients', {
            'hoTen': 'Benh nhan kiem chung phuc hoi', 'ngaySinh': '1995-01-01',
            'gioiTinh': 'M', 'soCmnd': identity}, (201,))
        return created['maBenhNhan']

    patient_id = patient()
    body = {'patientId': patient_id}
    barrier = threading.Barrier(2, timeout=10)
    keys = {role: uuid.uuid4().hex for role in ['nurse', 'nurse2']}

    def issue(role):
        barrier.wait()
        # Validate the status/error pair here as the winner is nondeterministic.
        status, envelope = call(role, 'POST', PATH, body, (201, 409), keys[role], with_envelope=True)
        if status == 409:
            assert envelope['error']['code'] == 'PATIENT_ACCOUNT_ALREADY_ISSUED'
            return role, None
        outcome = envelope['data']
        assert outcome['patientId'] == patient_id and outcome['mustChangePassword'] is True
        return role, outcome

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(issue, ['nurse', 'nurse2']))
    winners = [(role, outcome) for role, outcome in results if outcome is not None]
    assert len(winners) == 1, 'Concurrent issuance must create exactly one account'
    winner, issued = winners[0]
    loser = next(role for role, outcome in results if outcome is None)
    replay = call(winner, 'POST', PATH, body, (200,), keys[winner])
    assert replay == issued
    call(loser, 'POST', PATH, body, (409,), keys[loser],
         expected_error='PATIENT_ACCOUNT_ALREADY_ISSUED')
    evidence['concurrentIssuance'] = {
        'patientId': patient_id, 'accountId': issued['accountId'],
        'actorIds': [claims[role]['sub'] for role in keys], 'winner': winner,
        'sameAccountOnReplay': True, 'loserRemainsConflict': True}

    lost_patient = patient()
    lost_key = uuid.uuid4().hex
    correlation = 'accept-lost-' + uuid.uuid4().hex
    upstream = {}

    class DropCommittedResponse(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Never log headers, credentials or request bodies.

        def do_POST(self):
            try:
                assert self.path == PATH
                payload = self.rfile.read(int(self.headers['Content-Length']))
                request = urllib.request.Request(gateway.rstrip('/') + PATH, payload, {
                    'Authorization': 'Bearer ' + tokens['nurse'],
                    'Content-Type': 'application/json', 'Idempotency-Key': lost_key,
                    'X-Correlation-Id': correlation}, method='POST')
                with urllib.request.urlopen(request, timeout=30) as response:
                    envelope = json.load(response)
                    upstream.update(status=response.status, data=envelope['data'],
                                    correlationId=envelope.get('correlationId'))
                # Gateway has returned the committed result; the client receives no HTTP response.
            except Exception as error:
                upstream['failureType'] = type(error).__name__
            finally:
                self.close_connection = True
                self.connection.shutdown(socket.SHUT_RDWR)
                self.connection.close()

    with HTTPServer(('127.0.0.1', 0), DropCommittedResponse) as proxy:
        proxy.timeout = 40
        worker = threading.Thread(target=proxy.handle_request, daemon=True)
        worker.start()
        request = urllib.request.Request(
            f'http://127.0.0.1:{proxy.server_port}' + PATH,
            json.dumps({'patientId': lost_patient}).encode(),
            {'Content-Type': 'application/json'}, method='POST')
        disconnected = False
        try:
            with urllib.request.urlopen(request, timeout=35):
                raise AssertionError('Fault proxy must not deliver an HTTP response')
        except http.client.RemoteDisconnected:
            disconnected = True
        finally:
            worker.join(timeout=40)
        assert not worker.is_alive() and disconnected
    assert upstream.get('status') == 201 and 'failureType' not in upstream
    evidence['checks'].append({
        'role': 'nurse', 'method': 'POST', 'path': PATH, 'status': 201,
        'expected': [201], 'errorCode': None, 'correlationId': upstream['correlationId'],
        'observation': 'proxy-upstream-only; client disconnected before response'})
    recovered = call('nurse', 'POST', PATH, {'patientId': lost_patient}, (200,), lost_key)
    assert recovered == upstream['data']
    call('nurse', 'POST', PATH, {'patientId': patient_id}, (409,), lost_key,
         expected_error='ACCOUNT_IDEMPOTENCY_KEY_REUSED')
    call('nurse', 'POST', PATH, {'patientId': lost_patient}, (409,),
         expected_error='PATIENT_ACCOUNT_ALREADY_ISSUED')
    evidence['lostResponseRecovery'] = {
        'patientId': lost_patient, 'accountId': recovered['accountId'],
        'upstreamStatus': 201, 'clientReceivedResponse': False,
        'sameAccountOnRetry': True, 'fault': 'connection-closed-after-gateway-response'}
