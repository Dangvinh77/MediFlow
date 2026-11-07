"""Give the cashier real money to work with, through the public APIs of two live visits.

paid: exam, Lab and medicine all collected, Lab resulted, medicine dispensed, record closed,
      so Lich su thu has a whole visit to show.
open: exam collected, then a quoted Lab and a prescription left unpaid, so the queue holds
      charges that can actually be taken and carried on to Lab and Pharmacy on stage.

Needs clinic hours, like the follow-up source in prepare_ui_demo. Never writes business SQL.
"""
import json
import time
import urllib.parse
import uuid

from prepare_ui_demo import SEED_LOGIN_EMAILS, ZONE, open_record, register_confirmed

PATIENT_EMAILS = {'paid': 'demo.case.883.30@example.test', 'open': 'demo.case.882.30@example.test'}
DRUG_KEYWORD, DRUG_NAME = 'DEMO-HIST-2025', 'Thuốc minh họa A'
PRICE_CODES = {'lab': 'LAB_CBC', 'drug': 'RX-DEMO'}
READY_ATTEMPTS = 30


def current_session(api, session):
    query = urllib.parse.urlencode({'departmentId': session['departmentId'], 'fromDate': session['sessionDate'],
                                    'toDate': session['sessionDate'], 'page': 0, 'size': 100})
    rows = api.call('admin', 'GET', '/api/v2/consultation-sessions?' + query)['content']
    return next(row for row in rows if row['sessionId'] == session['sessionId'])


def examine(api, session, patient_id, stage, diagnosis):
    # Every registration moves the session revision, so read it right before registering.
    registration_id = register_confirmed(api, current_session(api, session), patient_id, stage)
    record = open_record(api, registration_id, patient_id)
    if not record.get('diagnoses'):
        api.call('doctor', 'POST', '/api/v1/records/' + record['recordId'] + '/diagnoses',
                 {'diagnosisName': diagnosis, 'icdCode': 'Z00'}, expected=(201,))
    return registration_id, record


def charge(api, purpose, source_id, patient_id, registration_id):
    query = urllib.parse.urlencode({'purpose': purpose, 'sourceId': source_id,
                                    'patientId': patient_id, 'careEpisodeId': registration_id})
    return api.wait('cashier', '/api/v1/billing/payment-requests/by-source?' + query,
                    lambda row: True, expected=(200, 404, 503))


def order_lab(api, record, patient_id, department_id, registration_id, day, stage):
    order = api.call('doctor', 'POST', '/api/v1/records/' + record['recordId'] + '/lab-orders',
                     {'labType': 'CBC', 'requestedDate': day, 'priceCode': PRICE_CODES['lab']},
                     expected=(201,), stage=stage + '-order')
    test = api.call('doctor', 'POST', '/api/v1/lab', {
        'recordId': record['recordId'], 'patientId': patient_id, 'requestingDepartmentId': department_id,
        'labType': 'CBC', 'requestedDate': day, 'priceCode': PRICE_CODES['lab'],
        'sourceOrderId': order['sourceOrderId'], 'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': registration_id}, expected=(201,), stage=stage + '-test')
    return test, charge(api, 'LAB_TEST', test['testId'], patient_id, registration_id)


def prescribe(api, record, patient_id, doctor_id, department_id, registration_id, drug_id, day, stage):
    prescription = api.call('doctor', 'POST', '/api/v1/pharmacy/prescriptions', {
        'recordId': record['recordId'], 'patientId': patient_id, 'doctorId': doctor_id,
        'departmentId': department_id, 'prescribedDate': day,
        'lines': [{'drugId': drug_id, 'quantity': 2, 'dosage': 'Thuốc mô phỏng phục vụ demo, không dùng điều trị'}],
        'careContractVersion': 1, 'careContext': 'OUTPATIENT', 'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': registration_id, 'admissionId': None, 'priceCode': PRICE_CODES['drug'],
    }, expected=(200, 201), stage=stage + '-prescription')
    return prescription, charge(api, 'PRESCRIPTION', prescription['prescriptionId'], patient_id, registration_id)


def pay_lab(api, request, stage):
    body = api.state.get(stage, {}).get('body')
    if not body:
        quote = api.call('cashier', 'GET', '/api/v2/billing/payment-requests/' + request['paymentRequestId'] + '/quote')
        body = {'quoteId': quote['quoteId'], 'amount': quote['patientPayable'], 'currency': 'VND', 'paymentMethod': 'CASH'}
    return api.call('cashier', 'POST', '/api/v2/billing/payment-requests/' + request['paymentRequestId'] + '/lab-payments',
                    body, stage=stage)


def pay_prescription(api, request, stage):
    body = api.state.get(stage, {}).get('body') or {
        'idempotencyKey': str(uuid.uuid4()), 'amount': request['remainingAmount'], 'currency': 'VND',
        'paymentMethod': 'CASH', 'providerReference': None}
    return api.call('cashier', 'POST', '/api/v1/billing/payment-requests/' + request['paymentRequestId'] + '/payments',
                    body, stage=stage)


def until(read, message):
    """Clearance arrives by event after payment, so its consumers are polled, not assumed."""
    for attempt in range(READY_ATTEMPTS):
        result = read()
        if result:
            return result
        if attempt + 1 < READY_ATTEMPTS:
            time.sleep(2)
    raise RuntimeError(message)


def finish_lab(api, test, day):
    path = '/api/v1/lab/' + test['testId']
    current = until(lambda: (row if (row := api.call('lab', 'GET', path)).get('clearanceId') else None),
                    'Lab clearance did not arrive')
    if current['status'] == 'COMPLETED':
        return
    if current['status'] != 'IN_PROGRESS':
        api.call('lab', 'PUT', path + '/start', {})
    api.call('lab', 'PUT', path + '/results', {
        'results': [{'indicator': 'WBC', 'value': '7.0', 'unit': 'G/L', 'referenceRange': '4-10'}],
        'conclusion': 'Ket qua binh thuong', 'performedDate': day})


def dispense(api, prescription, stage):
    path = '/api/v1/pharmacy/prescriptions/' + prescription['prescriptionId'] + '/dispense'
    until(lambda: api.call('pharmacist', 'PUT', path, None, expected=(200, 201, 409, 422), stage=stage),
          'Prescription clearance did not arrive')


def prepare(api, fixtures, now):
    if not fixtures.get('historyReady'):
        return {'ready': False, 'limitation': 'Run again 07:00-16:45 Vietnam to prepare cashier visits.'}
    for role in ('admin', 'nurse', 'doctor', 'cashier', 'lab', 'pharmacist'):
        api.login(role, SEED_LOGIN_EMAILS[role])
    for role, email in PATIENT_EMAILS.items():
        api.login(role, email)
    day = str(now.astimezone(ZONE).date())
    session = next(row for row in fixtures['sessions'] if row['sessionDate'] == day)
    department_id, doctor_id = session['departmentId'], session['doctorId']
    drugs = api.call('doctor', 'GET', '/api/v1/pharmacy/drugs?'
                     + urllib.parse.urlencode({'keyword': DRUG_KEYWORD, 'page': 0, 'size': 20}))['content']
    drug_id = next(row['drugId'] for row in drugs if row['drugName'].endswith(DRUG_NAME))

    patient_id = api.claims['paid']['patientId']
    stage = 'cashier-paid-' + day
    registration_id, record = examine(api, session, patient_id, stage, 'Kham tong quat, da thu du')
    test, lab_request = order_lab(api, record, patient_id, department_id, registration_id, day, stage)
    pay_lab(api, lab_request, stage + '-lab-payment')
    finish_lab(api, test, day)
    prescription, rx_request = prescribe(api, record, patient_id, doctor_id, department_id, registration_id, drug_id, day, stage)
    pay_prescription(api, rx_request, stage + '-rx-payment')
    dispense(api, prescription, stage + '-dispense')
    if api.call('doctor', 'GET', '/api/v1/records/' + record['recordId'])['status'] != 'COMPLETED':
        api.call('doctor', 'PUT', '/api/v1/records/' + record['recordId'] + '/complete',
                 {'disposition': 'OUTPATIENT_FOLLOW_UP', 'dispositionNote': 'Lich su thu demo'})

    open_patient = api.claims['open']['patientId']
    stage = 'cashier-open-' + day
    open_id, open_record_row = examine(api, session, open_patient, stage, 'Cho thu xet nghiem va thuoc')
    open_test, open_lab = order_lab(api, open_record_row, open_patient, department_id, open_id, day, stage)
    open_rx, open_rx_request = prescribe(api, open_record_row, open_patient, doctor_id, department_id, open_id, drug_id, day, stage)
    return {'ready': True, 'date': day,
            'paid': {'patientId': patient_id, 'registrationId': registration_id},
            'open': {'patientId': open_patient, 'registrationId': open_id, 'testId': open_test['testId'],
                     'prescriptionId': open_rx['prescriptionId'],
                     'labPayable': open_lab['remainingAmount'], 'prescriptionPayable': open_rx_request['remainingAmount']}}


def write_fixtures(result, evidence):
    (evidence / 'cashier-fixtures.local.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('CASHIER DEMO READY' if result['ready'] else 'PARTIAL: ' + result['limitation'], flush=True)
