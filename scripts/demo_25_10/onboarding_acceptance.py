"""ONB assertions composed into the isolated scheduling acceptance journey.

Only synthetic patients are created. Credentials remain in memory and are never
added to evidence. No direct database reads or forged authentication are used.
"""
import time
import urllib.parse
import uuid


class OnboardingAcceptance:
    def __init__(self, call, login, tokens, claims, evidence):
        self.call, self.login = call, login
        self.tokens, self.claims, self.evidence = tokens, claims, evidence
        self.patient_id = self.issued = self.key = None

    def begin(self):
        # Resolve a fresh synthetic identity before creation, as reception does.
        identity = 'ONB' + uuid.uuid4().hex[:16]
        matches = self.call('nurse', 'GET', '/api/v1/patients?' + urllib.parse.urlencode(
            {'keyword': identity, 'page': 0, 'size': 20}))
        assert matches['totalElements'] == 0
        patient = self.call('nurse', 'POST', '/api/v1/patients', {
            'hoTen': 'Benh nhan tong duyet ONB', 'ngaySinh': '1995-01-01',
            'gioiTinh': 'M', 'soCmnd': identity}, (201,))
        self.patient_id = patient['maBenhNhan']
        body = {'patientId': self.patient_id}
        self.key = uuid.uuid4().hex
        self.issued = self.call('nurse', 'POST', '/api/v1/org/patient-accounts', body, (201,), self.key)
        assert self.issued['patientId'] == self.patient_id and self.issued['mustChangePassword'] is True
        assert self.issued['username'] == identity
        replay = self.call('nurse', 'POST', '/api/v1/org/patient-accounts', body, (200,), self.key)
        assert replay == self.issued
        self.call('nurse', 'POST', '/api/v1/org/patient-accounts', body, (409,),
                  expected_error='PATIENT_ACCOUNT_ALREADY_ISSUED')
        self.call('nurse', 'POST', '/api/v1/org/patient-accounts',
                  {'patientId': self.claims['other']['patientId']}, (409,), self.key,
                  expected_error='ACCOUNT_IDEMPOTENCY_KEY_REUSED')
        self.call('nurse', 'POST', '/api/v1/org/patient-accounts',
                  {'patientId': str(uuid.uuid4())}, (404,))
        for field, value in [('role', 'ADMIN'), ('staffId', str(uuid.uuid4())), ('replaceActiveAccount', True)]:
            self.call('nurse', 'POST', '/api/v1/org/patient-accounts', {**body, field: value}, (400,))
        self.call('doctor', 'POST', '/api/v1/org/patient-accounts', body, (403,))
        self.call('nurse', 'POST', '/api/v1/org/accounts', {
            'username': identity, 'password': '1', 'patientId': self.patient_id, 'role': 'ADMIN'}, (403,))
        self.evidence.update(patientId=self.patient_id, accountId=self.issued['accountId'],
                             patientLoginDeferred=True)
        return self.patient_id

    def release_results(self, record_id, test_id, recommendation, before_first_login=None):
        late_login = 'patient' not in self.tokens
        # Observe durable in-app history through authorized staff reads.
        history_path = f'/api/v1/notifications/patient/{self.patient_id}?page=0&size=100'
        expected_titles = {'Kết quả xét nghiệm đã có', 'Kết quả khám đã có', 'Có đề xuất tái khám'}
        for _ in range(30):
            history = self.call('nurse', 'GET', history_path)
            notices = [row for row in history['content'] if row['title'] in expected_titles]
            if len(notices) == 3:
                break
            time.sleep(2)
        assert {row['title'] for row in notices} == expected_titles and len(notices) == 3
        assert all(row['patientId'] == self.patient_id and row['channel'] == 'IN_APP' for row in notices)
        self.evidence['completionNoticeIds'] = [row['notificationId'] for row in notices]
        if before_first_login:
            assert late_login, 'Replay must be verified before the initial patient login'
            before_first_login(notices)
        if late_login:
            self.evidence['noticesBeforeFirstLogin'] = self.evidence['completionNoticeIds']
            self.first_login()
        self.evidence['firstLoginAfterClinicalCompletion'] = late_login
        self.completed_results(record_id, test_id, recommendation, notices)

    def first_login(self):
        assert 'patient' not in self.tokens, 'This must be the initial patient login'
        history_path = f'/api/v1/notifications/patient/{self.patient_id}?page=0&size=100'
        credentials = self.login('patient', self.issued['username'], '1')
        assert self.claims['patient']['mustChangePassword'] is True
        for path in ['/api/v1/records/mine', '/api/v1/lab/mine', history_path,
                     '/api/v2/outpatient-registrations/mine']:
            self.call('patient', 'GET', path, expected=(403,), expected_error='AUTH_PASSWORD_CHANGE_REQUIRED')
        self.call('patient', 'PUT', '/api/v1/org/accounts/me/password',
                  {'currentPassword': '1', 'newPassword': 'OnbRehearsal@2026'})
        self.call('patient', 'GET', '/api/v1/records/mine', expected=(401,))
        self.call('expired-session', 'POST', '/api/v1/auth/refresh',
                  {'refreshToken': credentials['refreshToken']}, (401,))
        self.call('expired-session', 'POST', '/api/v1/auth/login',
                  {'username': self.issued['username'], 'password': '1'}, (401,),
                  expected_error='AUTH_INVALID_CREDENTIALS')
        self.login('patient', self.issued['username'], 'OnbRehearsal@2026')
        assert self.claims['patient']['mustChangePassword'] is False
        replay = self.call('nurse', 'POST', '/api/v1/org/patient-accounts',
                           {'patientId': self.patient_id}, (200,), self.key)
        assert replay['accountId'] == self.issued['accountId'] and replay['mustChangePassword'] is False

    def pending_results(self, record_id, test_id):
        records = self.call('patient', 'GET', '/api/v1/records/mine?page=0&size=100')
        labs = self.call('patient', 'GET', '/api/v1/lab/mine?page=0&size=100')
        assert records['totalElements'] == 0 and labs['totalElements'] == 0
        assert not records['content'] and not labs['content']
        self.evidence['pendingResultsHidden'] = {'recordId': record_id, 'testId': test_id}

    def return_before_login(self, previous_registration_id, session, session_date):
        assert 'patient' not in self.tokens
        previous = self.call('nurse', 'GET', '/api/v2/outpatient-registrations/' + previous_registration_id)
        assert previous['status'] == 'COMPLETED'
        matches = self.call('nurse', 'GET', '/api/v1/patients?' + urllib.parse.urlencode({
            'keyword': self.issued['username'], 'page': 0, 'size': 20}))
        assert matches['totalElements'] == 1
        assert matches['content'][0]['maBenhNhan'] == self.patient_id
        # Fetch the current revision; capacity changed as the first visit was served.
        available = self.call('nurse', 'GET', '/api/v2/consultation-sessions?' + urllib.parse.urlencode({
            'departmentId': session['departmentId'], 'fromDate': str(session_date),
            'toDate': str(session_date), 'page': 0, 'size': 100}))
        current = next(item for item in available['content'] if item['sessionId'] == session['sessionId'])
        body = {'patientId': self.patient_id, 'sessionId': current['sessionId'],
                'expectedSessionRevision': current['revision'], 'receptionMode': 'AT_COUNTER',
                'visitType': 'NEW_VISIT'}
        key = uuid.uuid4().hex
        returned = self.call('nurse', 'POST', '/api/v2/outpatient-registrations', body, (201,), key)
        replay = self.call('nurse', 'POST', '/api/v2/outpatient-registrations', body, (201,), key)
        assert replay['registrationId'] == returned['registrationId'] != previous_registration_id
        assert returned['patientId'] == self.patient_id and returned['checkedInAt'] is not None
        assert returned['status'] == 'AWAITING_PAYMENT' and not returned['readyForExam']
        account = self.call('nurse', 'POST', '/api/v1/org/patient-accounts',
                            {'patientId': self.patient_id}, (200,), self.key)
        assert account == self.issued and account['mustChangePassword'] is True
        self.evidence['returnBeforeFirstLogin'] = {
            'patientId': self.patient_id, 'accountId': account['accountId'],
            'firstRegistrationId': previous_registration_id,
            'secondRegistrationId': returned['registrationId'], 'receptionMode': 'AT_COUNTER',
            'secondRegistrationStatus': returned['status'], 'mustChangePasswordBeforeLogin': True,
            'sessionDate': str(session_date), 'sameDaySyntheticReturn': True}

    def read_lab_during_exam(self, record_id, test_id, registration_id, request_id, quote, receipt):
        before = self.call('nurse', 'GET', '/api/v2/outpatient-registrations/' + registration_id)
        assert before['status'] == 'IN_EXAM'
        staff_records = self.call('doctor', 'GET', '/api/v1/records/patient/' + self.patient_id)
        record = next(row for row in staff_records if row['recordId'] == record_id)
        assert record['appointmentId'] == registration_id and record['status'] == 'OPEN'
        for _ in range(2):
            labs = self.call('patient', 'GET', '/api/v1/lab/mine?page=0&size=100')
            records = self.call('patient', 'GET', '/api/v1/records/mine?page=0&size=100')
            assert labs['totalElements'] == 1 and labs['content'][0]['testId'] == test_id
            assert labs['content'][0]['recordId'] == record_id
            assert labs['content'][0]['results'][0]['value'] == '7.0'
            assert records['totalElements'] == 0 and not records['content']
        after = self.call('nurse', 'GET', '/api/v2/outpatient-registrations/' + registration_id)
        assert after['status'] == before['status'] and after['recordId'] == before['recordId']
        discovery = self.call('cashier', 'GET',
                              '/api/v2/billing/payment-requests/by-registration?registrationId=' + registration_id)
        assert discovery['paymentRequestId'] == request_id
        current_quote = self.call('cashier', 'GET', '/api/v2/billing/payment-requests/' + request_id + '/quote')
        assert current_quote == quote
        current_receipt = self.call('cashier', 'GET', '/api/v2/billing/payment-operations/' +
                                    receipt['operation']['paymentOperationId'])
        assert current_receipt == receipt
        mine = self.call('patient', 'GET', '/api/v2/outpatient-registrations/mine?page=0&size=100')
        assert mine['totalElements'] == 1 and mine['content'][0]['registrationId'] == registration_id
        self.evidence['labReadDuringOpenVisit'] = {
            'registrationId': registration_id, 'recordId': record_id, 'testId': test_id,
            'registrationStatus': after['status'], 'reads': 2,
            'sameExamPaymentRequest': True, 'sameQuote': True, 'samePaymentReceipt': True,
            'ownRegistrationCount': mine['totalElements'], 'ownCompletedExamCount': records['totalElements']}

    def completed_results(self, record_id, test_id, recommendation, notices):
        history_path = f'/api/v1/notifications/patient/{self.patient_id}?page=0&size=100'
        records = self.call('patient', 'GET', '/api/v1/records/mine?page=0&size=100')
        labs = self.call('patient', 'GET', '/api/v1/lab/mine?page=0&size=100')
        assert [row['recordId'] for row in records['content']] == [record_id]
        assert [row['testId'] for row in labs['content']] == [test_id]
        self.evidence['ownExamResultCount'] = records['totalElements']
        self.evidence['ownLabResultCount'] = labs['totalElements']
        late_history = self.call('patient', 'GET', history_path)
        assert {row['notificationId'] for row in notices}.issubset(
            {row['notificationId'] for row in late_history['content']})
        # List query scope cannot be changed; staff detail routes remain forbidden.
        for root in ['/api/v1/records/mine', '/api/v1/lab/mine']:
            self.call('patient', 'GET', root + '?patientId=' + self.claims['other']['patientId'], expected=(400,))
        self.call('patient', 'GET', '/api/v1/records/' + record_id, expected=(403,))
        self.call('patient', 'GET', '/api/v1/lab/' + test_id, expected=(403,))
        for root, field, forbidden in [('/api/v1/records/mine', 'recordId', record_id),
                                       ('/api/v1/lab/mine', 'testId', test_id)]:
            other = self.call('other', 'GET', root + '?page=0&size=100')
            assert all(row[field] != forbidden for row in other['content'])
        self.call('other', 'GET', history_path, expected=(403, 404))
        self.call('other', 'GET', '/api/v1/notifications/' + notices[0]['notificationId'], expected=(403, 404))
        advice = '/api/v2/records/' + record_id + '/follow-up-recommendation'
        own = self.call('patient', 'GET', advice)
        assert own['recommendationId'] == recommendation['recommendationId']
        self.call('other', 'GET', advice, expected=(404,))

    def revisit(self, previous_registration_id, session, tomorrow, follow_up_session_id=None):
        if follow_up_session_id:
            available = self.call('admin', 'GET', '/api/v2/consultation-sessions?' + urllib.parse.urlencode({
                'departmentId': session['departmentId'], 'fromDate': str(tomorrow),
                'toDate': str(tomorrow), 'page': 0, 'size': 100}))
            next_session = next(item for item in available['content']
                                if item['sessionId'] == str(follow_up_session_id))
            assert next_session['doctorId'] == session['doctorId']
            assert next_session['departmentId'] == session['departmentId']
            assert next_session['sessionDate'] == str(tomorrow) and next_session['availableCount'] > 0
        else:
            next_session = self.call('admin', 'POST', '/api/v2/consultation-sessions', {
                'doctorId': session['doctorId'], 'departmentId': session['departmentId'],
                'roomId': session['roomId'], 'sessionDate': str(tomorrow),
                'startTime': '07:00', 'endTime': '17:00', 'breaks': [], 'capacity': 10}, (201,))
        body = {'patientId': self.patient_id, 'sessionId': next_session['sessionId'],
                'expectedSessionRevision': next_session['revision'], 'receptionMode': 'PREBOOKED',
                'visitType': 'FOLLOW_UP', 'previousRegistrationId': previous_registration_id,
                'followUpRecommendationId': self.evidence['recommendationId']}
        key = uuid.uuid4().hex
        follow_up = self.call('nurse', 'POST', '/api/v2/outpatient-registrations', body, (201,), key)
        replay = self.call('nurse', 'POST', '/api/v2/outpatient-registrations', body, (201,), key)
        assert follow_up['registrationId'] == replay['registrationId']
        assert follow_up['patientId'] == self.patient_id and follow_up['previousRegistrationId'] == previous_registration_id
        mine = self.call('patient', 'GET', '/api/v2/outpatient-registrations/mine?page=0&size=100')
        expected = {previous_registration_id, follow_up['registrationId']}
        if self.evidence.get('returnBeforeFirstLogin'):
            expected.add(self.evidence['returnBeforeFirstLogin']['secondRegistrationId'])
        assert {row['registrationId'] for row in mine['content']} == expected
        assert mine['totalElements'] == len(expected)
        self.evidence['followUpRegistrationId'] = follow_up['registrationId']
        self.evidence['followUpSessionId'] = next_session['sessionId']
        self.evidence['samePatientRegistrationCount'] = mine['totalElements']
        # Staff-assisted recovery uses the existing ADMIN command after identity verification.
        # This synthetic run verifies access/session effects, not a real person's identity.
        recovery = '/api/v1/org/accounts/' + self.issued['accountId'] + '/password'
        self.call('nurse', 'PUT', recovery, {'newPassword': 'RecoveredOnb@2026'}, (403,))
        self.call('admin', 'PUT', recovery, {'newPassword': 'RecoveredOnb@2026'})
        self.call('patient', 'GET', '/api/v1/records/mine', expected=(401,))
        self.call('expired-session', 'POST', '/api/v1/auth/login', {
            'username': self.issued['username'], 'password': 'OnbRehearsal@2026'}, (401,),
            expected_error='AUTH_INVALID_CREDENTIALS')
        self.login('patient', self.issued['username'], 'RecoveredOnb@2026')
        assert self.claims['patient']['sub'] == self.issued['accountId']
        assert self.claims['patient']['mustChangePassword'] is False
        recovered = self.call('patient', 'GET', '/api/v1/records/mine?page=0&size=100')
        assert recovered['totalElements'] == 1
        self.evidence['recoveryKeepsAccount'] = True
