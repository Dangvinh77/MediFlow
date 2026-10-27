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

    def release_results(self, record_id, test_id, recommendation):
        assert 'patient' not in self.tokens, 'Patient must not log in before clinical work completes'
        # Observe durable in-app history through authorized staff reads before any patient login.
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
        self.evidence['noticesBeforeFirstLogin'] = [row['notificationId'] for row in notices]
        self.evidence['firstLoginAfterClinicalCompletion'] = True
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

    def revisit(self, previous_registration_id, session, tomorrow):
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
        assert {row['registrationId'] for row in mine['content']} == {
            previous_registration_id, follow_up['registrationId']}
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
