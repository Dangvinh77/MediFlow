"""Exercise fresh V2 scheduling through a disposable rehearsal Gateway.

Requires the synthetic demo accounts and --scheduling rehearsal. It creates real demo
business records; use only the disposable stack. Never logs credentials or tokens.
"""
import argparse
import base64
import datetime as dt
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gateway', default='http://127.0.0.1:28080')
    parser.add_argument('--output', default='scripts/demo_25_10/evidence/scheduling.local.json')
    parser.add_argument('--booking-only', action='store_true',
                        help='Validate booking/payment for tomorrow without starting an examination')
    args = parser.parse_args()
    tokens, claims, checks = {}, {}, []
    today = dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date()
    clinic_now = dt.datetime.now(dt.timezone(dt.timedelta(hours=7)))
    session_date = today + dt.timedelta(days=1) if args.booking_only else today
    if not args.booking_only and clinic_now.hour >= 17:
        parser.error('Full examination rehearsal requires a session before 17:00 Asia/Ho_Chi_Minh; use --booking-only after hours')

    def call(role, method, path, body=None, expected=(200,), key=None):
        headers = {'Content-Type': 'application/json', 'X-Correlation-Id': 'accept-opd-' + uuid.uuid4().hex}
        if role in tokens:
            headers['Authorization'] = 'Bearer ' + tokens[role]
        if method in ('POST', 'PUT'):
            headers['Idempotency-Key'] = key or uuid.uuid4().hex
        req = urllib.request.Request(args.gateway.rstrip('/') + path,
                                     None if body is None else json.dumps(body).encode(), headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                status, envelope = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, envelope = error.code, json.load(error)
        checks.append({'role': role, 'method': method, 'path': path, 'status': status,
                       'correlationId': envelope.get('correlationId'), 'expected': list(expected)})
        print(f'{role} {method} {path}: {status}', flush=True)
        if status not in expected:
            raise AssertionError(f'{role} {method} {path}: {status} {envelope.get("error")}')
        # Gateway authentication returns its own DTO; business APIs use ApiResponse.
        return envelope.get('data') if 'data' in envelope else envelope

    def login(role, username):
        data = call(role, 'POST', '/api/v1/auth/login',
                    {'username': username, 'password': 'MediFlowDemo@2026'})
        tokens[role] = data['accessToken']
        # Only read returned identity for test inputs. The server verifies every token.
        segment = tokens[role].split('.')[1]
        claims[role] = json.loads(base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4)))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {'gateway': args.gateway, 'date': str(today), 'sessionDate': str(session_date),
              'scope': 'booking-payment' if args.booking_only else 'exam-lab-follow-up',
              'checks': checks, 'passed': False}
    try:
        for role, username in [('admin', 'demo.admin'), ('nurse', 'demo.nurse'), ('doctor', 'demo.doctor'),
                               ('cashier', 'demo.cashier'), ('patient', 'demo.patient'), ('other', 'demo.patient2'),
                               ('lab', 'demo.lab')]:
            login(role, username)
        doctor_id = claims['doctor']['staffId']
        patient_id = claims['patient']['patientId']
        staff = call('admin', 'GET', '/api/v1/org/staff/' + doctor_id)
        department_id = staff['departmentId']
        rooms = call('admin', 'GET', '/api/v2/org/consultation-rooms?' + urllib.parse.urlencode(
            {'departmentId': department_id, 'page': 0, 'size': 100}))['content']
        if not rooms:
            raise AssertionError('Seed has no active consultation room for the doctor department')
        session = call('admin', 'POST', '/api/v2/consultation-sessions', {
            'doctorId': doctor_id, 'departmentId': department_id, 'roomId': rooms[0]['roomId'],
            'sessionDate': str(session_date), 'startTime': '07:00', 'endTime': '17:00', 'breaks': [], 'capacity': 10}, (201,))
        body = {'patientId': patient_id, 'sessionId': session['sessionId'],
                'expectedSessionRevision': session['revision'],
                'receptionMode': 'PREBOOKED' if args.booking_only else 'AT_COUNTER', 'visitType': 'NEW_VISIT'}
        key = uuid.uuid4().hex
        registration = call('nurse', 'POST', '/api/v2/outpatient-registrations', body, (201,), key)
        replay = call('nurse', 'POST', '/api/v2/outpatient-registrations', body, (201,), key)
        assert registration['registrationId'] == replay['registrationId']
        registration_id = registration['registrationId']
        result['registrationId'] = registration_id
        call('other', 'GET', '/api/v2/outpatient-registrations/' + registration_id, expected=(403, 404))
        request = None
        for attempt in range(30):
            request = call('cashier', 'GET', '/api/v2/billing/payment-requests/by-registration?registrationId=' + registration_id,
                           expected=(200, 404, 503))
            if request:
                break
            time.sleep(2)
        assert request, 'Billing registration context was not available before the deadline'
        request_id = request['paymentRequestId']
        quote = call('cashier', 'GET', '/api/v2/billing/payment-requests/' + request_id + '/quote')
        assert float(quote['grossAmount']) == 100000 and float(quote['supportAmount']) == 50000
        assert float(quote['patientPayable']) == 50000
        payment_body = {'reservationId': request['reservationId'],
                        'expectedReservationRevision': request['reservationRevision'], 'quoteId': quote['quoteId'],
                        'amount': quote['patientPayable'], 'currency': 'VND', 'paymentMethod': 'CASH'}
        payment_key = uuid.uuid4().hex
        receipt = call('cashier', 'POST', '/api/v2/billing/payment-requests/' + request_id + '/payments',
                       payment_body, (200, 202), payment_key)
        operation_id = receipt['operation']['paymentOperationId']
        for attempt in range(30):
            if receipt['operation']['status'] != 'PREPARING':
                break
            time.sleep(2)
            receipt = call('cashier', 'GET', '/api/v2/billing/payment-operations/' + operation_id)
        assert receipt['operation']['status'] == 'COMPLETED'
        replay = call('cashier', 'POST', '/api/v2/billing/payment-requests/' + request_id + '/payments',
                      payment_body, (200,), payment_key)
        assert replay['payment']['transactionId'] == receipt['payment']['transactionId']
        result.update(paymentRequestId=request_id, paymentOperationId=operation_id,
                      transactionId=receipt['payment']['transactionId'])
        for attempt in range(30):
            confirmed = call('patient', 'GET', '/api/v2/outpatient-registrations/' + registration_id)
            if confirmed['status'] == 'CONFIRMED':
                break
            time.sleep(2)
        assert confirmed['status'] == 'CONFIRMED'
        call('doctor', 'GET', '/api/v2/consultation-sessions/mine?date=' + str(session_date))
        if args.booking_only:
            assert not confirmed['readyForExam'] and confirmed['checkedInAt'] is None
            result['passed'] = True
            print('PASS: booking-only, V2 registration, own access, supported quote, payment replay and confirmation; examination/Lab/follow-up NOT exercised')
            return
        assert confirmed['readyForExam']
        call('doctor', 'PUT', '/api/v1/appointments/' + registration_id + '/start-exam', {})
        records = call('doctor', 'GET', '/api/v1/records/patient/' + patient_id)
        record = next(record for record in records if record['appointmentId'] == registration_id)
        record_id = record['recordId']
        call('doctor', 'POST', '/api/v1/records/' + record_id + '/diagnoses',
             {'diagnosisName': 'Chan doan demo tong duyet', 'icdCode': 'Z00'}, (201,))
        order = call('doctor', 'POST', '/api/v1/records/' + record_id + '/lab-orders',
                     {'labType': 'CBC', 'requestedDate': str(today), 'priceCode': 'LAB_CBC'}, (201,))
        test = call('doctor', 'POST', '/api/v1/lab', {
            'recordId': record_id, 'patientId': patient_id, 'requestingDepartmentId': department_id,
            'labType': 'CBC', 'requestedDate': str(today), 'priceCode': 'LAB_CBC',
            'sourceOrderId': order['sourceOrderId'], 'careEpisodeType': 'OUTPATIENT_VISIT',
            'careEpisodeId': registration_id}, (201,))
        test_id = test['testId']
        result['testId'] = test_id
        call('doctor', 'PUT', '/api/v1/records/' + record_id + '/complete',
             {'disposition': 'OUTPATIENT_FOLLOW_UP', 'dispositionNote': 'Pending Lab must block completion'}, (409, 422))
        call('lab', 'PUT', '/api/v1/lab/' + test_id + '/start', {}, (409, 422))
        discovery_path = '/api/v1/billing/payment-requests/by-source?' + urllib.parse.urlencode({
            'purpose': 'LAB_TEST', 'sourceId': test_id, 'patientId': patient_id, 'careEpisodeId': registration_id})
        lab_request = None
        for attempt in range(30):
            lab_request = call('cashier', 'GET', discovery_path, expected=(200, 404, 503))
            if lab_request:
                break
            time.sleep(2)
        if not lab_request:
            raise AssertionError('Lab payment request projection did not arrive')
        lab_request_id = lab_request['paymentRequestId']
        lab_quote = call('cashier', 'GET', '/api/v2/billing/payment-requests/' + lab_request_id + '/quote')
        assert float(lab_quote['grossAmount']) == 120000 and float(lab_quote['patientPayable']) == 36000
        lab_pay_body = {'quoteId': lab_quote['quoteId'], 'amount': lab_quote['patientPayable'],
                        'currency': 'VND', 'paymentMethod': 'CASH'}
        lab_key = uuid.uuid4().hex
        lab_payment = call('cashier', 'POST', '/api/v2/billing/payment-requests/' + lab_request_id + '/lab-payments',
                           lab_pay_body, (200,), lab_key)
        lab_replay = call('cashier', 'POST', '/api/v2/billing/payment-requests/' + lab_request_id + '/lab-payments',
                          lab_pay_body, (200,), lab_key)
        assert lab_payment['transactionId'] == lab_replay['transactionId']
        for attempt in range(30):
            refreshed = call('lab', 'GET', '/api/v1/lab/' + test_id)
            if refreshed.get('clearanceId'):
                break
            time.sleep(2)
        call('lab', 'PUT', '/api/v1/lab/' + test_id + '/start', {})
        call('lab', 'PUT', '/api/v1/lab/' + test_id + '/results', {
            'results': [{'indicator': 'WBC', 'value': '7.0', 'unit': 'G/L', 'referenceRange': '4-10'}],
            'conclusion': 'Ket qua tong duyet binh thuong', 'performedDate': str(today)})
        call('doctor', 'PUT', '/api/v1/records/' + record_id + '/complete',
             {'disposition': 'OUTPATIENT_FOLLOW_UP', 'dispositionNote': 'Hen tai kham demo'})
        tomorrow = today + dt.timedelta(days=1)
        recommendation = call('doctor', 'POST', '/api/v2/records/' + record_id + '/follow-up-recommendations', {
            'recommendedFrom': str(tomorrow), 'recommendedThrough': str(tomorrow + dt.timedelta(days=7)),
            'reason': 'Kiem tra lai sau dieu tri demo', 'preparationInstructions': 'Mang ket qua lan truoc'}, (201,))
        mine = call('patient', 'GET', '/api/v2/outpatient-registrations/mine?page=0&size=100')
        own = next(item for item in mine['content'] if item['registrationId'] == registration_id)
        assert own['recordId'] == record_id and own['followUpRecommendation']['recommendationId'] == recommendation['recommendationId']
        sources = call('nurse', 'GET', '/api/v2/outpatient-registrations/follow-up-sources?' + urllib.parse.urlencode(
            {'patientId': patient_id, 'page': 0, 'size': 100}))
        assert any(source['registrationId'] == registration_id for source in sources['content'])
        result.update(recordId=record_id, recommendationId=recommendation['recommendationId'], passed=True)
        print('PASS: V2 registration, quote/payment replay, confirmed exam, paid Lab/result, completed record, patient follow-up advice')
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
