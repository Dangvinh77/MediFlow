"""Seed one patient per hand-off point, so each presenter starts from their own screen.

Every case is a real visit driven through the public Gateway, the same commands the UI sends;
no business SQL is written. Each case uses its own seeded patient (one visit per patient per day):

  vinh_exam_ready  patient03  exam fee paid, registration CONFIRMED  -> room number, start exam
  vinh_lab_paid    patient04  in exam, CBC ordered and paid (READY)  -> lab tech enters result, second tech releases
  vinh_lab_done    patient05  in exam, CBC released                  -> doctor reads result, prescribes, completes
  huy_prescribe    patient06  in exam with a diagnosis               -> doctor writes the prescription
  huy_dispense     patient07  prescription paid                      -> pharmacist dispenses

Lộc's exam-fee charge cannot be seeded at start-up: an unpaid registration holds its slot for
15 minutes only. Create it right before Lộc presents:

    scripts\\run\\seed-demo.bat -ExamChargeNow

The lab and prescription charges Lộc collects, and the patient inbox he shows, come from
prepare_cashier_demo (demo.case.882.30 open charges, demo.case.883.30 completed visit).
patient01 (backup patient09) stays untouched for the end-to-end run; patient10 has no account,
so Hoàng Anh can issue one and then re-issue its password.

Needs clinic hours (07:00-16:45 Vietnam) and the fixtures of prepare_ui_demo. Reruns on the same
day resume from the journal and never create a second visit.
"""
import argparse
import datetime as dt
import json
import time
import urllib.parse

import lab_v2
from prepare_cashier_demo import (charge, current_session, examine, pay_lab, pay_prescription, prescribe, until,
                                  DRUG_KEYWORD, DRUG_NAME)
from prepare_ui_demo import HERE, SEED_LOGIN_EMAILS, ZONE, DemoApi, register_confirmed, validate_manifest

PASSWORD = 'MediFlowDemo@2026'
# CCCD of the seeded patient profiles (snapshot MediFlow_Demo_Database_2026-10-25.sql).
CASES = {
    'vinh_exam_ready': '999881000003',
    'vinh_lab_paid': '999881000004',
    'vinh_lab_done': '999881000005',
    'huy_prescribe': '999881000006',
    'huy_dispense': '999881000007',
}
EXAM_CHARGE_CCCD = '999881000008'
RESERVED = {'end_to_end': '999881000001', 'end_to_end_backup': '999881000009', 'account_issuance': '999881000010'}
STAFF_ROLES = ('admin', 'nurse', 'doctor', 'cashier', 'lab', 'lab2', 'pharmacist')


def find_patient(api, cccd):
    query = urllib.parse.urlencode({'keyword': cccd, 'page': 0, 'size': 10})
    rows = api.call('nurse', 'GET', '/api/v1/patients?' + query)['content']
    match = [row for row in rows if row.get('soCmnd') == cccd]
    if len(match) != 1:
        raise RuntimeError('Seeded patient profile missing or ambiguous: CCCD ' + cccd)
    return match[0]


def stocked_drug(api):
    query = urllib.parse.urlencode({'keyword': DRUG_KEYWORD, 'page': 0, 'size': 20})
    drugs = api.call('doctor', 'GET', '/api/v1/pharmacy/drugs?' + query)['content']
    return next(row['drugId'] for row in drugs if row['drugName'].endswith(DRUG_NAME))


def lab_with_clearance(api, test_id):
    path = '/api/v1/lab/' + test_id
    return until(lambda: (row if (row := api.call('lab', 'GET', path)).get('clearanceId') else None),
                 'Lab clearance did not arrive')


def ordered_and_paid(api, session, patient_id, day, stage, diagnosis):
    registration_id, record = examine(api, session, patient_id, stage, diagnosis)
    test = lab_v2.order(api, record['recordId'], patient_id, session['departmentId'], registration_id, day, stage)
    request = charge(api, 'LAB_TEST', test['testId'], patient_id, registration_id)
    if request.get('remainingAmount'):
        pay_lab(api, request, stage + '-lab-payment')
    return registration_id, record, test, lab_with_clearance(api, test['testId'])


def vinh_exam_ready(api, session, patient_id, day, stage):
    # Every registration moves the session revision, so read it right before registering.
    return {'registrationId': register_confirmed(api, current_session(api, session), patient_id, stage)}


def vinh_lab_paid(api, session, patient_id, day, stage):
    registration_id, record, test, current = ordered_and_paid(api, session, patient_id, day, stage,
                                                              'Sot keo dai, can xet nghiem cong thuc mau')
    return {'registrationId': registration_id, 'recordId': record['recordId'], 'testId': test['testId'],
            'labStatus': current['status']}


def vinh_lab_done(api, session, patient_id, day, stage):
    registration_id, record, test, current = ordered_and_paid(api, session, patient_id, day, stage,
                                                              'Viem hong cap, da co ket qua xet nghiem')
    if current['status'] != 'COMPLETED':
        if current['status'] != 'IN_PROGRESS':
            api.call('lab', 'PUT', '/api/v1/lab/' + test['testId'] + '/start', {})
        lab_v2.release(api, test['testId'], day, conclusion='Ket qua trong gioi han, bac si doc va ke don')
    return {'registrationId': registration_id, 'recordId': record['recordId'], 'testId': test['testId'],
            'labStatus': 'COMPLETED'}


def huy_prescribe(api, session, patient_id, day, stage):
    registration_id, record = examine(api, session, patient_id, stage, 'Viem mui hong, chi dinh dung thuoc')
    return {'registrationId': registration_id, 'recordId': record['recordId']}


def huy_dispense(api, session, patient_id, day, stage):
    registration_id, record = examine(api, session, patient_id, stage, 'Dau dau, da ke don va thu tien thuoc')
    prescription, request = prescribe(api, record, patient_id, session['doctorId'], session['departmentId'],
                                      registration_id, stocked_drug(api), day, stage)
    if request.get('remainingAmount'):
        pay_prescription(api, request, stage + '-rx-payment')
    path = '/api/v1/pharmacy/prescriptions/' + prescription['prescriptionId']
    until(lambda: api.call('pharmacist', 'GET', path).get('paymentConfirmed'),
          'Prescription clearance did not arrive')
    return {'registrationId': registration_id, 'recordId': record['recordId'],
            'prescriptionId': prescription['prescriptionId']}


STEPS = {'vinh_exam_ready': vinh_exam_ready, 'vinh_lab_paid': vinh_lab_paid, 'vinh_lab_done': vinh_lab_done,
         'huy_prescribe': huy_prescribe, 'huy_dispense': huy_dispense}


def login_staff(api):
    for role in STAFF_ROLES:
        api.login(role, SEED_LOGIN_EMAILS[role])


def today_session(api, fixtures, now):
    day = str(now.astimezone(ZONE).date())
    session = next(row for row in fixtures['sessions'] if row['sessionDate'] == day)
    return day, session


def prepare(api, fixtures, now):
    if not fixtures.get('historyReady'):
        return {'ready': False, 'limitation': 'Run again 07:00-16:45 Vietnam to prepare the staged visits.'}
    login_staff(api)
    day, session = today_session(api, fixtures, now)
    result = {'ready': True, 'date': day, 'cases': {}, 'failed': {}, 'reserved': {}}
    for key, cccd in CASES.items():
        patient = find_patient(api, cccd)
        try:
            state = STEPS[key](api, session, patient['maBenhNhan'], day, 'staged-' + key + '-' + day)
        except Exception as error:  # One stuck case must not hide the others; report it instead.
            result['failed'][key] = {'cccd': cccd, 'hoTen': patient['hoTen'], 'error': str(error)}
            continue
        result['cases'][key] = {'cccd': cccd, 'hoTen': patient['hoTen'], 'patientId': patient['maBenhNhan'], **state}
    for key, cccd in RESERVED.items():
        patient = find_patient(api, cccd)
        result['reserved'][key] = {'cccd': cccd, 'hoTen': patient['hoTen'], 'patientId': patient['maBenhNhan']}
    result['ready'] = not result['failed']
    return result


def exam_charge_now(api, fixtures, now):
    """Register EXAM_CHARGE_CCCD at the counter and leave the exam fee unpaid for 15 minutes."""
    login_staff(api)
    day, session = today_session(api, fixtures, now)
    patient = find_patient(api, EXAM_CHARGE_CCCD)
    stage = 'staged-loc-exam-charge-' + day
    body = api.state.get(stage, {}).get('body') or {
        'patientId': patient['maBenhNhan'], 'sessionId': session['sessionId'],
        'expectedSessionRevision': current_session(api, session)['revision'],
        'receptionMode': 'AT_COUNTER', 'visitType': 'NEW_VISIT'}
    registration = api.call('nurse', 'POST', '/api/v2/outpatient-registrations', body, expected=(201,), stage=stage)
    current = api.call('nurse', 'GET', '/api/v2/outpatient-registrations/' + registration['registrationId'])
    return {'date': day, 'cccd': EXAM_CHARGE_CCCD, 'hoTen': patient['hoTen'], 'patientId': patient['maBenhNhan'],
            'registrationId': registration['registrationId'], 'status': current['status'],
            'reservationExpiresAt': current.get('reservationExpiresAt')}


LABELS = {
    'vinh_exam_ready': ('Vinh', 'Đã thu phí khám, phiếu CONFIRMED', 'demo.staff04 cấp số phòng → demo.staff03 bắt đầu khám'),
    'vinh_lab_paid': ('Vinh', 'Đang khám, CBC đã thu tiền (READY)', 'demo.staff15 nhận mẫu, nhập, gửi duyệt → demo.staff16 phát hành'),
    'vinh_lab_done': ('Vinh', 'Đang khám, CBC đã phát hành', 'demo.staff03 đọc kết quả → kê đơn → hoàn tất hồ sơ'),
    'huy_prescribe': ('Huy', 'Đang khám, đã có chẩn đoán', 'demo.staff03 tạo đơn thuốc'),
    'huy_dispense': ('Huy', 'Đơn đã thu tiền thuốc', 'demo.staff17 xác nhận cấp phát'),
}


def write_fixtures(result, evidence):
    (evidence / 'staged-fixtures.local.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    lines = ['# Bệnh nhân seed theo từng điểm bàn giao', '']
    if not result.get('date'):
        lines.append('**CHƯA chuẩn bị:** ' + result['limitation'])
    else:
        lines += ['Ngày: ' + result['date'] + '. Mật khẩu mọi tài khoản demo: `' + PASSWORD + '`.', '',
                  '| Người demo | Bệnh nhân | CCCD | Trạng thái sẵn | Làm tiếp | Mã |', '|---|---|---|---|---|---|']
        for key, (owner, state, next_step) in LABELS.items():
            case = result['cases'].get(key)
            if case:
                codes = ' · '.join(f'{name}: `{case[name]}`' for name in ('registrationId', 'recordId', 'testId', 'prescriptionId') if case.get(name))
                lines.append(f'| {owner} | {case["hoTen"]} | {case["cccd"]} | {state} | {next_step} | {codes} |')
            else:
                failure = result['failed'].get(key, {})
                lines.append(f'| {owner} | {failure.get("hoTen", "?")} | {failure.get("cccd", CASES[key])} | **LỖI** | {failure.get("error", "")} | |')
        lines += ['', 'Lộc: khoản phí khám chờ thu tạo ngay trước lúc trình bày (giữ suất 15 phút):',
                  '`scripts\\run\\seed-demo.bat -ExamChargeNow` (CCCD ' + EXAM_CHARGE_CCCD + ').', '']
        for key, patient in result.get('reserved', {}).items():
            lines.append(f'- Giữ nguyên ({key}): {patient["hoTen"]}, CCCD {patient["cccd"]}')
    lines += ['', 'Kịch bản: scripts/demo_25_10/DEMO_THEO_THU_TU.md']
    (evidence / 'DEMO-STAGED.local.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    if result.get('ready'):
        print('STAGED DEMO READY: ' + str(evidence / 'DEMO-STAGED.local.md'), flush=True)
    else:
        print('STAGED DEMO PARTIAL: ' + (result.get('limitation') or json.dumps(result['failed'], ensure_ascii=False)), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--exam-charge-now', action='store_true',
                        help='Create the unpaid exam-fee registration for Lộc right now (holds 15 minutes).')
    options = parser.parse_args()
    manifest = json.loads((HERE / 'evidence/active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    fixtures = json.loads((evidence / 'ui-demo-fixtures.local.json').read_text(encoding='utf-8'))
    api = DemoApi(gateway, evidence / 'staged-journal.local.json')
    now = dt.datetime.now(ZONE)
    if options.exam_charge_now:
        result = exam_charge_now(api, fixtures, now)
        print(f'EXAM CHARGE READY: {result["hoTen"]} (CCCD {result["cccd"]}) {result["status"]}, '
              f'hết hạn giữ suất {result["reservationExpiresAt"]}', flush=True)
        return result
    started = time.monotonic()
    result = prepare(api, fixtures, now)
    result['seconds'] = round(time.monotonic() - started, 1)
    write_fixtures(result, evidence)
    return result


if __name__ == '__main__':
    main()
