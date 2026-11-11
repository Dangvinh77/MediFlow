"""Prepare UI fixtures through the active disposable Gateway; never writes business SQL.

Run after rehearse_snapshot.py --scheduling --keep. Full preparation needs clinic
hours (07:00-16:45 Vietnam); outside that window only schedules are prepared.
Tokens/passwords are kept in memory. A journal retains command keys and results.
"""
import argparse
import base64
import datetime as dt
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

HERE = Path(__file__).resolve().parent
ZONE = dt.timezone(dt.timedelta(hours=7))
SEED_LOGIN_EMAILS = {
    'admin': 'demo.staff01@example.test',
    'nurse': 'demo.staff04@example.test',
    'doctor': 'demo.staff03@example.test',
    'cashier': 'demo.staff19@example.test',
    'lab': 'demo.staff15@example.test',
    'lab2': 'demo.staff16@example.test',
    'pharmacist': 'demo.staff17@example.test',
    'patient': 'demo.patient01@example.test',
    'patient2': 'demo.patient02@example.test',
}


class ApiError(RuntimeError):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def demo_dates(now):
    today = now.astimezone(ZONE).date()
    return today, today + dt.timedelta(days=1)


def can_prepare_history(now):
    local = now.astimezone(ZONE).time().replace(tzinfo=None)
    return dt.time(7) <= local < dt.time(16, 45)


def validate_manifest(manifest):
    gateway = manifest.get('gateway', '')
    parsed = urllib.parse.urlsplit(gateway)
    evidence = Path(manifest['evidence']).resolve()
    root = (HERE / 'evidence').resolve()
    if (parsed.scheme != 'http' or parsed.hostname != '127.0.0.1' or parsed.port != 28080
            or parsed.path or parsed.query or parsed.fragment or parsed.username
            or not manifest.get('scheduling') or not evidence.is_relative_to(root)
            or Path(manifest['stopFile']).resolve() != evidence / 'stop'
            or Path(manifest['stopFile']).exists()):
        raise ValueError('Use the active disposable scheduling rehearsal only')
    return gateway, evidence


class DemoApi:
    def __init__(self, gateway, journal, cancel_file=None):
        self.gateway, self.journal = gateway, journal
        self.cancel_file = cancel_file
        self.state = json.loads(journal.read_text(encoding='utf-8')) if journal.exists() else {}
        self.tokens, self.claims = {}, {}

    def save(self):
        temporary = self.journal.with_suffix('.tmp')
        temporary.write_text(json.dumps(self.state, ensure_ascii=False, indent=2), encoding='utf-8')
        temporary.replace(self.journal)

    def call(self, role, method, path, body=None, expected=(200,), stage=None, journal_omit=()):
        if self.cancel_file and self.cancel_file.exists():
            raise InterruptedError('Demo preparation stopped by request')
        command = None
        if stage:
            # Fixed public demo credentials are supplied in memory on each retry, never saved.
            stored_body = {k: v for k, v in body.items() if k not in journal_omit} if journal_omit else body
            command = self.state.setdefault(stage, {'key': str(uuid.uuid4()), 'body': stored_body, 'path': path})
            if command['body'] != stored_body or command['path'] != path:
                raise ValueError('Prepared command changed; reconcile the journal: ' + stage)
            if 'result' in command:
                return command['result']
            self.save()  # Persist identity before sending an uncertain write.
        headers = {'Content-Type': 'application/json', 'X-Correlation-Id': 'ui-seed-' + uuid.uuid4().hex}
        if role in self.tokens:
            headers['Authorization'] = 'Bearer ' + self.tokens[role]
        if method in ('POST', 'PUT'):
            headers['Idempotency-Key'] = command['key'] if command else str(uuid.uuid4())
        request = urllib.request.Request(self.gateway + path, None if body is None else json.dumps(body).encode(),
                                         headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status, envelope = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, envelope = error.code, json.load(error)
        if status not in expected:
            raise ApiError(status, f'{method} {path}: HTTP {status}, {envelope.get("error")}')
        if status >= 400:
            # Expected polling failures are not data or successful journal results.
            return None
        result = envelope.get('data') if 'data' in envelope else envelope
        if command is not None:
            command['result'] = result
            self.save()
        return result

    def login(self, role, username):
        # Gateway may be healthy before its first discovery refresh sees Core.
        # Retry authentication availability only; never retry an uncertain business write here.
        for attempt in range(30):
            try:
                result = self.call(role, 'POST', '/api/v1/auth/login',
                                   {'username': username, 'password': 'MediFlowDemo@2026'})
                break
            except ApiError as error:
                if error.status not in (502, 503, 504) or attempt == 29:
                    raise
                time.sleep(2)
        self.tokens[role] = result['accessToken']
        segment = result['accessToken'].split('.')[1]
        self.claims[role] = json.loads(base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4)))

    def wait(self, role, path, predicate, expected=(200,)):
        for _ in range(45):
            result = self.call(role, 'GET', path, expected=expected)
            if result and predicate(result):
                return result
            time.sleep(2)
        raise RuntimeError('Projection did not become ready: ' + path)


def register_confirmed(api, session, patient_id, stage):
    """Register at the counter and collect the exam fee; resumes the same journaled commands."""
    # Reuse exact initial revision on resume; never replace an uncertain command.
    body = api.state.get(stage, {}).get('body') or {
        'patientId': patient_id, 'sessionId': session['sessionId'],
        'expectedSessionRevision': session['revision'], 'receptionMode': 'AT_COUNTER', 'visitType': 'NEW_VISIT'}
    registration = api.call('nurse', 'POST', '/api/v2/outpatient-registrations', body,
                            expected=(201,), stage=stage)
    registration_id = registration['registrationId']
    current = api.call('nurse', 'GET', '/api/v2/outpatient-registrations/' + registration_id)
    if current['status'] in ('CONFIRMED', 'IN_EXAM', 'COMPLETED'):
        return registration_id
    request = api.wait('cashier', '/api/v2/billing/payment-requests/by-registration?registrationId=' + registration_id,
                       lambda row: True, expected=(200, 404, 503))
    payment_stage = stage + '-payment'
    payment = api.state.get(payment_stage, {}).get('body')
    if not payment:
        quote = api.call('cashier', 'GET', '/api/v2/billing/payment-requests/' + request['paymentRequestId'] + '/quote')
        payment = {'reservationId': request['reservationId'], 'expectedReservationRevision': request['reservationRevision'],
                   'quoteId': quote['quoteId'], 'amount': quote['patientPayable'], 'currency': 'VND', 'paymentMethod': 'CASH'}
    receipt = api.call('cashier', 'POST', '/api/v2/billing/payment-requests/' + request['paymentRequestId'] + '/payments',
                       payment, expected=(200, 202), stage=payment_stage)
    if receipt['operation']['status'] == 'PREPARING':
        receipt = api.wait('cashier', '/api/v2/billing/payment-operations/' + receipt['operation']['paymentOperationId'],
                           lambda row: row['operation']['status'] != 'PREPARING')
    if receipt['operation']['status'] != 'COMPLETED':
        raise RuntimeError('History payment did not complete; reconcile before continuing')
    api.wait('nurse', '/api/v2/outpatient-registrations/' + registration_id, lambda row: row['status'] == 'CONFIRMED')
    return registration_id


def open_record(api, registration_id, patient_id):
    """Start the exam if needed and return the full medical record of this visit."""
    records = api.call('doctor', 'GET', '/api/v1/records/patient/' + patient_id)
    record = next((row for row in records if row['appointmentId'] == registration_id), None)
    if record is None:
        api.call('doctor', 'PUT', '/api/v1/appointments/' + registration_id + '/start-exam', {})
        records = api.call('doctor', 'GET', '/api/v1/records/patient/' + patient_id)
        record = next(row for row in records if row['appointmentId'] == registration_id)
    return api.call('doctor', 'GET', '/api/v1/records/' + record['recordId'])


def grant_lab_release(api):
    """Both seed lab techs may release, so one enters/submits and the other releases (four-eyes rule)."""
    for role in ('lab', 'lab2'):
        # 409 CAPABILITY_GRANT_ACTIVE on a rerun means the grant is already in place.
        api.call('admin', 'POST', '/api/v2/org/staff/' + api.claims[role]['staffId'] + '/capability-grants',
                 {'capability': 'LAB_RESULT_RELEASE', 'reason': 'Demo: cap quyen phat hanh ket qua xet nghiem'},
                 expected=(201, 409))


def prepare(api, now, schedule_only=False):
    today, tomorrow = demo_dates(now)
    for role in ('admin', 'nurse', 'doctor', 'cashier', 'lab', 'lab2', 'patient', 'patient2'):
        api.login(role, SEED_LOGIN_EMAILS[role])
    grant_lab_release(api)
    doctor_id = api.claims['doctor']['staffId']
    staff = api.call('admin', 'GET', '/api/v1/org/staff/' + doctor_id)
    department_id = staff['departmentId']
    department = api.call('admin', 'GET', '/api/v1/org/departments/' + department_id)
    query = urllib.parse.urlencode({'departmentId': department_id, 'page': 0, 'size': 100})
    rooms = api.call('admin', 'GET', '/api/v2/org/consultation-rooms?' + query)['content']
    # Keep the all-day seed doctor fixture out of the supplemental rotating calendar room.
    rooms = [room for room in rooms if room['roomName'] != 'Phong kham lich day demo']
    if not rooms:
        raise RuntimeError('Scheduling room fixture missing; restart with --scheduling')
    report = {'date': str(today), 'historyReady': False, 'department': department,
              'doctor': staff, 'room': rooms[0], 'sessions': [], 'patients': {}}
    for role in ('patient', 'patient2'):
        patient = api.call('nurse', 'GET', '/api/v1/patients/' + api.claims[role]['patientId'])
        report['patients'][role] = patient
    for day in (today, tomorrow):
        if day == today and now.astimezone(ZONE).hour >= 17:
            continue
        assignment = {'doctorId': doctor_id, 'departmentId': department_id, 'roomId': rooms[0]['roomId'],
                      'startTime': '07:00', 'endTime': '17:00'}
        # The doctor calendar shows the assigned shift above the sessions cut from it.
        shift = api.call('admin', 'POST', '/api/v2/doctor-work-shifts', {**assignment, 'shiftDate': str(day)},
                         expected=(200, 201), stage='shift-' + str(day))
        session = api.call('admin', 'POST', '/api/v2/doctor-work-shifts/' + shift['shiftId'] + '/consultation-sessions',
                           {**assignment, 'sessionDate': str(day), 'breaks': [], 'capacity': 40},
                           expected=(200, 201), stage='session-' + str(day))
        # Re-read because capacity/revision may have changed since the first run.
        rows = api.call('admin', 'GET', '/api/v2/consultation-sessions?' + query
                        + '&fromDate=' + str(day) + '&toDate=' + str(day))['content']
        report['sessions'].append(next(row for row in rows if row['sessionId'] == session['sessionId']))
    if schedule_only or not can_prepare_history(now):
        report['limitation'] = 'Schedules only. Run again 07:00-16:45 Vietnam to prepare the completed V2 follow-up source.'
        return report
    session = next(row for row in report['sessions'] if row['sessionDate'] == str(today))
    patient_id = api.claims['patient2']['patientId']
    stage = 'history-' + str(today)
    registration_id = register_confirmed(api, session, patient_id, stage)
    record = open_record(api, registration_id, patient_id)
    record_path = '/api/v1/records/' + record['recordId']
    if record['status'] != 'COMPLETED':
        if not record.get('diagnoses'):
            api.call('doctor', 'POST', record_path + '/diagnoses',
                     {'diagnosisName': 'Du lieu nen tai kham demo UI', 'icdCode': 'Z09'}, expected=(201,))
        api.call('doctor', 'PUT', record_path + '/complete',
                 {'disposition': 'OUTPATIENT_FOLLOW_UP', 'dispositionNote': 'Synthetic UI preparation; no Lab ordered in this source visit.'})
    advice = api.call('doctor', 'POST', '/api/v2/records/' + record['recordId'] + '/follow-up-recommendations',
                      {'recommendedFrom': str(today), 'recommendedThrough': str(today + dt.timedelta(days=7)),
                       'reason': 'Tai kham mo phong de demo UI', 'preparationInstructions': 'Mang ket qua lan truoc'},
                      expected=(200, 201), stage=stage + '-advice')
    sources = api.call('nurse', 'GET', '/api/v2/outpatient-registrations/follow-up-sources?'
                       + urllib.parse.urlencode({'patientId': patient_id, 'page': 0, 'size': 100}))['content']
    if not any(row['registrationId'] == registration_id for row in sources):
        raise RuntimeError('Completed source is not available to nurse follow-up picker')
    report.update(historyReady=True, followUpSourceRegistrationId=registration_id, followUpAdvice=advice)
    return report


def write_fixtures(result, evidence):
    output = evidence / 'ui-demo-fixtures.local.json'
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    lines = ['# Dữ liệu chọn trên UI', '', 'Ngày chuẩn bị: ' + result['date'], '',
             'Buổi khám: 07:00–17:00, 40 chỗ; chọn đúng ngày bên dưới.', '',
             'Khoa: ' + result['department']['departmentName'],
             'Bác sĩ: ' + result['doctor']['fullName'],
             'Phòng: ' + result['room']['roomName'], '',
             '| Tình huống | Chọn bệnh nhân | CCCD để tìm |', '|---|---|---|',
             '| Bệnh nhân mới | Tự tạo hồ sơ mới trên UI | Chọn CCCD tổng hợp chưa tồn tại |']
    for role, title in [('patient', 'Bệnh nhân cũ, khám mới'), ('patient2', 'Bệnh nhân tái khám')]:
        patient = result['patients'][role]
        lines.append(f'| {title} | {patient["hoTen"]} | {patient["soCmnd"]} |')
    lines += ['', 'Ngày có lịch: ' + ', '.join(row['sessionDate'] for row in result['sessions']), '',
              '**Có lượt nguồn tái khám hợp lệ.**' if result['historyReady'] else '**CHƯA có lượt nguồn tái khám:** ' + result['limitation'], '',
              'Chưa đăng ký ba lượt bạn sẽ demo. Một lượt nền của demo.patient02@example.test được thu phí và hoàn tất để làm nguồn tái khám khi historyReady=true.', '',
              'Hướng dẫn: scripts/demo_25_10/DEMO_UI_TUNG_BUOC.md']
    (evidence / 'DEMO-DATA.local.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('UI FIXTURES: ' + str(output), flush=True)
    print('UI DATA: ' + str(evidence / 'DEMO-DATA.local.md'), flush=True)
    print('FOLLOW-UP SOURCE READY' if result['historyReady'] else 'PARTIAL: ' + result['limitation'], flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schedule-only', action='store_true')
    options = parser.parse_args()
    manifest = json.loads((HERE / 'evidence/active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    names = manifest.get('containers', [])
    if len(names) != 2 or not all(name.startswith('mediflow-rehearsal-') for name in names):
        raise ValueError('Disposable rehearsal container identities are missing')
    containers = json.loads(subprocess.check_output(['docker', 'inspect', *names], text=True, encoding='utf-8'))
    if len(containers) != 2 or not all(row['State']['Running'] for row in containers):
        raise ValueError('Disposable rehearsal is stopped; start backend again')
    api = DemoApi(gateway, evidence / 'ui-demo-journal.local.json')
    result = prepare(api, dt.datetime.now(ZONE), options.schedule_only)
    write_fixtures(result, evidence)
    return result


if __name__ == '__main__':
    main()
