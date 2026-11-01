"""Populate staffing and a continuous 07–17 demo calendar through the disposable Gateway.

Only Organization room bootstrap uses SQL in seed_outpatient_scheduling.sql.
Every staff/account/shift/session command uses public APIs with a resumable journal.
No patients, bookings, payments or medical records are created by this script.
"""
import datetime as dt
import calendar as month_calendar
import json
from pathlib import Path
import urllib.parse
import uuid

HERE = Path(__file__).resolve().parent
START, END = dt.date(2026, 11, 1), dt.date(2027, 1, 31)
SLOTS = (('07:00', '12:00'), ('12:00', '17:00'))
ROOM_NAME = 'Phong kham lich day demo'
PASSWORD = 'MediFlowDemo@2026'  # Public synthetic fixture only; never reset existing accounts.


def dates(start, end):
    if start > end:
        raise ValueError('Start date must not exceed end date')
    while start <= end:
        yield start
        start += dt.timedelta(days=1)


def pages(api, path, **query):
    page = 0
    while True:
        result = api.call('admin', 'GET', path + '?' + urllib.parse.urlencode({**query, 'page': page, 'size': 100}))
        yield from result['content']
        page += 1
        if page >= result['totalPages']:
            break


def staff_plan(department):
    kind = department['departmentType']
    name = department['departmentName'].casefold()
    if kind == 'CLINICAL':
        return ['DOCTOR'] * 6 + ['NURSE'] * 4
    if kind == 'PARACLINICAL':
        return ['PHARMACIST' if any(word in name for word in ('dược', 'duoc', 'pharma')) else 'TECHNICIAN'] * 10
    cashier = any(word in name for word in ('tài chính', 'tai chinh', 'viện phí', 'vien phi', 'thu ngân', 'thu ngan', 'finance', 'billing'))
    return ['CASHIER' if cashier else 'ADMINISTRATIVE'] * 10


def verify(api, result):
    verified = 0
    accounts = {row['accountId']: row for row in pages(api, '/api/v1/org/accounts')}
    for department in result['departments']:
        department_id = department['departmentId']
        live_staff = {row['staffId']: row for row in pages(api, '/api/v1/org/staff', departmentId=department_id, activeOnly='true')}
        if len(live_staff) < 10 or any(
                row['staffId'] not in live_staff
                or any(live_staff[row['staffId']].get(field) != row.get(field)
                       for field in ('departmentId', 'jobTitle', 'licenseNumber'))
                for row in department['staff']):
            raise RuntimeError('Staffing verification failed: ' + department['departmentName'])
        if department['departmentType'] != 'CLINICAL':
            continue
        if not any(room['roomId'] == department['room']['roomId'] for room in pages(
                api, '/api/v2/org/consultation-rooms', departmentId=department_id)):
            raise RuntimeError('Calendar room no longer eligible; reconcile before continuing')
        wanted = {row['sessionId']: row for row in department['sessions']}
        observed = {}
        # The public schedule endpoint accepts at most 31 inclusive days per query.
        day = START
        while day <= END:
            last = min(END, dt.date(day.year, day.month, month_calendar.monthrange(day.year, day.month)[1]))
            for row in pages(api, '/api/v2/consultation-sessions', departmentId=department_id,
                             fromDate=str(day), toDate=str(last)):
                if row['sessionId'] in wanted:
                    observed[row['sessionId']] = row
            day = last + dt.timedelta(days=1)
        for session_id, expected in wanted.items():
            current = observed.get(session_id)
            fields = ('doctorId', 'departmentId', 'roomId', 'sessionDate', 'startTime', 'endTime', 'capacity')
            if (not current or current['status'] != 'OPEN' or current['availableCount'] < 1
                    or any(current[field] != expected[field] for field in fields)):
                raise RuntimeError('Calendar coverage changed; reconcile the journal/session: ' + session_id)
        verified += len(observed)
        for doctor in department['doctors']:
            account = accounts.get(doctor['accountId'])
            if (not account or not account['isActive'] or account['staffId'] != doctor['staffId']
                    or account['role'] != 'DOCTOR' or account['username'] != doctor['username']):
                raise RuntimeError('Demo doctor account changed; no password or access reset was attempted')
    result['verifiedSessions'] = verified


def prepare(api):
    api.login('admin', 'demo.admin')
    departments = api.call('admin', 'GET', '/api/v1/org/departments?activeOnly=true')
    if not departments:
        raise RuntimeError('No active departments in the restored demo')
    result = {'fromDate': str(START), 'throughDate': str(END), 'days': len(list(dates(START, END))), 'departments': []}
    for department in departments:
        if not department['active']:
            continue
        department_id = department['departmentId']
        token = uuid.uuid5(uuid.NAMESPACE_URL, 'mediflow-demo-calendar/' + department_id).hex[:10]
        row = {**department, 'staff': [], 'doctors': [], 'sessions': []}
        room = None
        if department['departmentType'] == 'CLINICAL':
            room = next((r for r in pages(api, '/api/v2/org/consultation-rooms', departmentId=department_id)
                         if r['roomName'] == ROOM_NAME), None)
            if not room:
                raise RuntimeError('Calendar room missing. Restart start-backend.bat to apply the room bootstrap.')
            row['room'] = room
        for index, title in enumerate(staff_plan(department), 1):
            identity = '98' + str(uuid.uuid5(uuid.NAMESPACE_URL, f'{department_id}/roster/{index}').int % 10**10).zfill(10)
            label = {'DOCTOR': 'BS', 'NURSE': 'ĐD', 'TECHNICIAN': 'KTV', 'PHARMACIST': 'DS', 'CASHIER': 'Thu ngân', 'ADMINISTRATIVE': 'Hành chính'}[title]
            full_name = f'{label} Demo {department["departmentName"][:55]} {index:02d}'
            staff = api.call('admin', 'POST', '/api/v1/org/staff', {
                'identityNumber': identity, 'fullName': full_name, 'departmentId': department_id,
                'jobTitle': title, 'specialization': department['departmentName'][:100],
                'licenseNumber': f'DEMO-RANGE-{token}-{index:02d}' if title in ('DOCTOR', 'NURSE', 'PHARMACIST') else None,
                'phoneNumber': None, 'email': f'roster.{token}.{index:02d}@example.test',
            }, expected=(200, 201), stage=f'roster-{department_id}-{index:02d}')
            row['staff'].append(staff)
            if title == 'DOCTOR':
                username = f'demo.roster.{token}.bs{index:02d}'
                account = api.call('admin', 'POST', '/api/v1/org/accounts', {
                    'username': username, 'password': PASSWORD, 'staffId': staff['staffId'], 'patientId': None, 'role': 'DOCTOR',
                }, expected=(200, 201), stage=f'roster-account-{department_id}-{index:02d}', journal_omit=('password',))
                row['doctors'].append({'staffId': staff['staffId'], 'fullName': full_name,
                                       'accountId': account['accountId'], 'username': username})
        if room:
            for day_index, day in enumerate(dates(START, END)):
                for slot_index, (start, end) in enumerate(SLOTS):
                    doctor = row['doctors'][(day_index * 2 + slot_index) % len(row['doctors'])]
                    body = {'doctorId': doctor['staffId'], 'departmentId': department_id, 'roomId': room['roomId'],
                            'shiftDate': str(day), 'startTime': start, 'endTime': end}
                    stage = f'calendar-{department_id}-{day}-{slot_index}'
                    shift = api.call('admin', 'POST', '/api/v2/doctor-work-shifts', body, expected=(200, 201), stage=stage + '-shift')
                    session = api.call('admin', 'POST', '/api/v2/doctor-work-shifts/' + shift['shiftId'] + '/consultation-sessions',
                                       {k: v for k, v in {**body, 'sessionDate': str(day), 'breaks': [], 'capacity': 40}.items() if k != 'shiftDate'},
                                       expected=(200, 201), stage=stage + '-session')
                    row['sessions'].append(session)
                if day.day == month_calendar.monthrange(day.year, day.month)[1] or day == END:
                    print(f'CALENDAR MONTH: department {department_id}; through {day}; {len(row["sessions"])} sessions', flush=True)
        result['departments'].append(row)
        # ASCII console output also works in the default Windows code page.
        print(f'CALENDAR PREPARED: department {department_id}; 10 staff; {len(row["sessions"])} sessions', flush=True)
    verify(api, result)
    return result


def write_fixtures(result, evidence):
    (evidence / 'calendar-fixtures.local.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    lines = ['# Nhân sự và lịch khám demo', '', f'01/11/2026–31/01/2027, {result["days"]} ngày, 07:00–12:00 và 12:00–17:00 mỗi ngày, kể cả cuối tuần.',
             'Mỗi buổi 40 chỗ, chưa đăng ký bệnh nhân. Giờ Việt Nam; không khám trước ngày/giờ thực.',
             'Mật khẩu ban đầu của tài khoản bác sĩ mẫu: `MediFlowDemo@2026`. Không đặt lại mật khẩu khi chạy lại.', '',
             '| Khoa | Nhân sự bổ sung | Buổi đã kiểm | Phòng |', '|---|---|---|---|']
    for row in result['departments']:
        lines.append(f'| {row["departmentName"]} | {len(row["staff"])} | {len(row["sessions"])} | {row.get("room", {}).get("roomName", "Không lập lịch khám bác sĩ")} |')
    lines += ['', '## Tài khoản bác sĩ theo khoa', '', '| Khoa | Bác sĩ | Tên đăng nhập |', '|---|---|---|']
    for row in result['departments']:
        for doctor in row['doctors']:
            lines.append(f'| {row["departmentName"]} | {doctor["fullName"]} | `{doctor["username"]}` |')
    lines += ['', 'Nurse chọn khoa/ngày → Tải buổi khám → Chọn buổi. Khi khám, đăng nhập đúng bác sĩ trên buổi đã chọn theo bảng này, không mặc định demo.doctor.',
              'Ngày hôm qua có lịch để xem nhưng không đăng ký mới được. Sau 17:00 chọn ngày sau để đăng ký trước, chờ đúng buổi mới khám.',
              'Ngày ngoài khoảng trên không được bảo đảm lịch. Khoa xét nghiệm/dược/hành chính chỉ bổ sung nhân sự.',
              'Nếu ca/buổi/tài khoản đã bị sửa hoặc hủy sau seed, script báo đối soát, không tự mở lại hay khôi phục quyền.']
    output = evidence / 'DEMO-CALENDAR.local.md'
    output.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'CALENDAR READY: {result["verifiedSessions"]} sessions verified; {output}', flush=True)


def main():
    from prepare_ui_demo import DemoApi, validate_manifest
    manifest = json.loads((HERE / 'evidence' / 'active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    write_fixtures(prepare(DemoApi(gateway, evidence / 'calendar-journal.local.json')), evidence)


if __name__ == '__main__':
    main()
