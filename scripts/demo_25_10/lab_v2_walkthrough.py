"""Walk one Lab v2 result through the public Gateway of the active disposable rehearsal.

order (catalogue GLU or CBC: `python lab_v2_walkthrough.py CBC`) -> pay -> specimen collected/accepted -> draft -> submit -> self-release refused
-> second tech releases -> flag from Patient's sex/age -> doctor acknowledges a critical notice (GLU 27.5) -> amendment with a reason -> re-release.
Needs clinic hours and a prepared UI demo (prepare_ui_demo). Never writes business SQL.
"""
import datetime as dt
import json
import sys
import time

from lab_v2 import SERVICES, values
from prepare_cashier_demo import charge, current_session, examine, pay_lab, until
from prepare_ui_demo import HERE, SEED_LOGIN_EMAILS, ZONE, ApiError, DemoApi, validate_manifest

# Never the UI demo patients (patient/patient2) nor the cashier demo visits.
RESERVED_LOGINS = ('patient', 'patient2')
def expect_error(api, role, method, path, body, status, code):
    try:
        api.call(role, method, path, body)
    except ApiError as error:
        if error.status == status and code in str(error):
            return str(error)
        raise
    raise AssertionError(f'{method} {path} should fail with {status} {code}')


def walk(api, fixtures, now, code='GLU'):
    service = SERVICES[code]
    for role in ('admin', 'nurse', 'doctor', 'cashier', 'lab', 'lab2'):
        api.login(role, SEED_LOGIN_EMAILS[role])
    reserved = set()
    for role in RESERVED_LOGINS:
        api.login(role, SEED_LOGIN_EMAILS[role])
        reserved.add(api.claims[role]['patientId'])
    day = str(now.astimezone(ZONE).date())
    session = next(row for row in fixtures['sessions'] if row['sessionDate'] == day)
    rows = api.call('nurse', 'GET', '/api/v1/patients?keyword=Demo&page=0&size=50')['content']
    candidates = [row['maBenhNhan'] for row in reversed(rows) if row['maBenhNhan'] not in reserved]
    patient_id = candidates[list(SERVICES).index(code)]  # one patient per service: one visit per day
    stage = 'lab-v2-' + code + '-' + day
    steps = []

    registration_id, record = examine(api, session, patient_id, stage, 'Demo Lab v2 ' + code)
    order = api.call('doctor', 'POST', '/api/v1/records/' + record['recordId'] + '/lab-orders',
                     {'labType': service['name'], 'requestedDate': day, 'priceCode': service['price'], 'serviceCode': code},
                     expected=(201,), stage=stage + '-order')
    test = api.call('doctor', 'POST', '/api/v1/lab', {
        'recordId': record['recordId'], 'patientId': patient_id, 'requestingDepartmentId': session['departmentId'],
        'labType': service['name'], 'requestedDate': day, 'priceCode': service['price'], 'serviceCode': code,
        'sourceOrderId': order['sourceOrderId'], 'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': registration_id}, expected=(201,), stage=stage + '-test')
    test_id = test['testId']
    steps.append(('order', test_id))
    template = api.call('lab', 'GET', '/api/v2/lab/' + test_id + '/template')
    steps.append(('template', template['serviceCode']))

    request = charge(api, 'LAB_TEST', test_id, patient_id, registration_id)
    pay_lab(api, request, stage + '-lab-payment')
    path = '/api/v1/lab/' + test_id
    current = until(lambda: (row if (row := api.call('lab', 'GET', path)).get('clearanceId') else None),
                    'Lab clearance did not arrive')
    if current['status'] == 'READY':
        api.call('lab', 'PUT', path + '/start', {})
    steps.append(('paid-and-started', current['status']))

    specimens = api.call('lab', 'GET', '/api/v2/lab/' + test_id + '/specimens')
    if not any(row['decision'] == 'ACCEPTED' for row in specimens):
        specimen = api.call('nurse', 'POST', '/api/v2/lab/' + test_id + '/specimens', {'specimenType': service['specimen']},
                            expected=(200, 201))
        api.call('lab', 'PUT', '/api/v2/lab/specimens/' + specimen['specimenId'] + '/decision',
                 {'decision': 'ACCEPTED'})
    steps.append(('specimen', 'ACCEPTED'))

    report = None
    try:
        report = api.call('lab', 'GET', '/api/v2/lab/' + test_id + '/report')
    except ApiError as error:
        if error.status != 404:
            raise
    if report is None or report['status'] == 'DRAFT':
        report = api.call('lab', 'PUT', '/api/v2/lab/' + test_id + '/report/draft', {
            'draftRevision': report['draftRevision'] if report else 0,
            'values': values(service['values']),
            'conclusion': 'Ket qua mo phong phuc vu demo', 'performedDate': day})
        report = api.call('lab', 'POST', '/api/v2/lab/' + test_id + '/report/submission',
                          {'draftRevision': report['draftRevision']}, expected=(200, 201))
    steps.append(('submitted', report['status']))
    if report['status'] == 'PENDING_REVIEW':
        steps.append(('self-release-refused', expect_error(
            api, 'lab', 'POST', '/api/v2/lab/' + test_id + '/report/release', None, 403, 'LAB_SELF_RELEASE')))
        report = api.call('lab2', 'POST', '/api/v2/lab/' + test_id + '/report/release', expected=(200, 201))
    steps.append(('released', report['status'], report['values'][0]['flag']))
    if report['values'][0]['flag'] == 'UNDETERMINED':
        # Patient gives Lab sex and date of birth at release; every demo patient is an adult.
        raise AssertionError('Reference range not applied: is Core reachable from Lab as patient-service?')

    if report['values'][0]['flag'] in ('CRITICAL_LOW', 'CRITICAL_HIGH'):
        notices = until(lambda: [row for row in api.call('doctor', 'GET', '/api/v2/lab/critical-notices?status=OPEN')
                                 if row['testId'] == test_id] or None, 'Critical notice did not reach the doctor')
        ack = api.call('doctor', 'POST', '/api/v2/lab/' + test_id + '/critical-notices/' + notices[0]['noticeId']
                       + '/acknowledgement', expected=(200, 201))
        steps.append(('critical-acknowledged', ack['status']))
    else:
        steps.append(('not-critical', report['values'][0]['flag']))

    amended = api.call('lab', 'POST', '/api/v2/lab/' + test_id + '/report/amendments',
                       {'reason': 'Nhap nham don vi, do lai mau'}, expected=(200, 201), stage=stage + '-amendment')
    if amended['status'] == 'DRAFT':
        amended = api.call('lab', 'PUT', '/api/v2/lab/' + test_id + '/report/draft', {
            'draftRevision': amended['draftRevision'], 'values': values(service['amended']),
            'conclusion': 'Ket qua sau khi do lai mau', 'performedDate': day})
        amended = api.call('lab', 'POST', '/api/v2/lab/' + test_id + '/report/submission',
                           {'draftRevision': amended['draftRevision']}, expected=(200, 201))
        amended = api.call('lab2', 'POST', '/api/v2/lab/' + test_id + '/report/release', expected=(200, 201))
    steps.append(('amendment-released', amended['resultVersion'], amended['values'][0]['flag']))

    def amended_documents():
        docs = api.call('doctor', 'GET', '/api/v2/records/' + record['recordId'] + '/documents')
        return docs if any(d.get('resultVersion') == amended['resultVersion'] for d in docs) else None

    documents = until(amended_documents, 'Clinical did not receive the amended result')
    lab_doc = next(d for d in documents if d.get('resultVersion') == amended['resultVersion'])
    steps.append(('clinical-document', lab_doc['resultVersion'], len(lab_doc.get('previousRevisions') or [])))
    return {'testId': test_id, 'recordId': record['recordId'], 'registrationId': registration_id, 'steps': steps}


def main():
    manifest = json.loads((HERE / 'evidence/active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    fixtures = json.loads((evidence / 'ui-demo-fixtures.local.json').read_text(encoding='utf-8'))
    api = DemoApi(gateway, evidence / 'lab-v2-journal.local.json')
    started = time.monotonic()
    code = sys.argv[1] if len(sys.argv) > 1 else 'GLU'
    result = walk(api, fixtures, dt.datetime.now(ZONE), code)
    result['seconds'] = round(time.monotonic() - started, 1)
    (evidence / ('lab-v2-walkthrough-' + code + '.local.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2),
                                                            encoding='utf-8')
    for step in result['steps']:
        print('LAB V2:', *step, flush=True)
    print('LAB V2 WALKTHROUGH PASSED', flush=True)


if __name__ == '__main__':
    main()
