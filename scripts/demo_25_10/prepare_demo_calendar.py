"""Populate rotating doctors and a continuous 07–17 demo calendar through the disposable Gateway.

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
# One month holding the whole current week, so the department week board is never half empty.
# Clinical refuses sessions for past dates (SESSION_DATE_PAST); the month starts on the run date.
DAYS_BEFORE, DAYS_AFTER = 0, 30
ROSTER_DOCTORS = 3
ZONE = dt.timezone(dt.timedelta(hours=7))
SLOTS = (('07:00', '12:00'), ('12:00', '17:00'))
ROOM_NAME = 'Phong kham lich day demo'
ADMIN_LOGIN_EMAIL = 'demo.staff01@example.test'


def window(today):
    return today - dt.timedelta(days=DAYS_BEFORE), today + dt.timedelta(days=DAYS_AFTER)


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
    # Only doctors take part in the calendar; other roster rows had no account and no flow.
    return ['DOCTOR'] * ROSTER_DOCTORS if department['departmentType'] == 'CLINICAL' else []


def verify(api, result):
    verified = 0
    start, end = dt.date.fromisoformat(result['fromDate']), dt.date.fromisoformat(result['throughDate'])
    for department in result['departments']:
        department_id = department['departmentId']
        live_staff = {row['staffId']: row for row in pages(api, '/api/v1/org/staff', departmentId=department_id, activeOnly='true')}
        if any(
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
        day = start
        while day <= end:
            last = min(end, dt.date(day.year, day.month, month_calendar.monthrange(day.year, day.month)[1]))
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
            if doctor['staffId'] not in live_staff or live_staff[doctor['staffId']]['jobTitle'] != 'DOCTOR':
                raise RuntimeError('Calendar doctor staffing changed; reconcile before continuing')
    result['verifiedSessions'] = verified


def prepare(api, today=None):
    first_day, last_day = window(today or dt.datetime.now(ZONE).date())
    api.login('admin', ADMIN_LOGIN_EMAIL)
    departments = api.call('admin', 'GET', '/api/v1/org/departments?activeOnly=true')
    if not departments:
        raise RuntimeError('No active departments in the restored demo')
    result = {'fromDate': str(first_day), 'throughDate': str(last_day), 'days': len(list(dates(first_day, last_day))), 'departments': []}
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
            # Identity survives a new run date, so a rerun finds the same doctors.
            identity = '98' + str(uuid.uuid5(uuid.NAMESPACE_URL, f'{department_id}/roster/{index}').int % 10**10).zfill(10)
            full_name = f'BS Demo {department["departmentName"][:55]} {index:02d}'
            staff = api.call('admin', 'POST', '/api/v1/org/staff', {
                'identityNumber': identity, 'fullName': full_name, 'departmentId': department_id,
                'jobTitle': title, 'specialization': department['departmentName'][:100],
                'licenseNumber': f'DEMO-RANGE-{token}-{index:02d}',
                'phoneNumber': None, 'email': f'roster.{token}.{index:02d}@example.test',
            }, expected=(200, 201), stage=f'roster-{department_id}-{index:02d}')
            row['staff'].append(staff)
            # The rotating roster is synthetic scheduling data, not an invitation
            # workflow. Do not issue credentials or send mail to example.test.
            row['doctors'].append({'staffId': staff['staffId'], 'fullName': full_name})
        if room:
            for day_index, day in enumerate(dates(first_day, last_day)):
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
        result['departments'].append(row)
        # ASCII console output also works in the default Windows code page.
        print(f'CALENDAR PREPARED: department {department_id}; {len(row["staff"])} doctors; {len(row["sessions"])} sessions', flush=True)
    verify(api, result)
    return result


def write_fixtures(result, evidence):
    (evidence / 'calendar-fixtures.local.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    lines = ['# Nhân sự và lịch khám demo', '', f'{result["fromDate"]} đến {result["throughDate"]}, {result["days"]} ngày, 07:00–12:00 và 12:00–17:00 mỗi ngày, kể cả cuối tuần.',
             'Mỗi buổi 40 chỗ, chưa đăng ký bệnh nhân. Giờ Việt Nam; không khám trước ngày/giờ thực.',
             '| Khoa | Bác sĩ luân phiên | Buổi đã kiểm | Phòng |', '|---|---|---|---|']
    for row in result['departments']:
        lines.append(f'| {row["departmentName"]} | {len(row["staff"])} | {len(row["sessions"])} | {row.get("room", {}).get("roomName", "Không lập lịch khám bác sĩ")} |')
    lines += ['', '## Bác sĩ luân phiên trong lịch', '', '| Khoa | Bác sĩ | Tài khoản bổ sung |', '|---|---|---|']
    for row in result['departments']:
        for doctor in row['doctors']:
            lines.append(f'| {row["departmentName"]} | {doctor["fullName"]} | Không tạo tài khoản |')
    lines += ['', 'Nhân sự bác sĩ luân phiên chỉ phục vụ dữ liệu lịch demo; script không cấp tài khoản và không gửi email đến địa chỉ tổng hợp.',
              'Để thao tác hồ sơ khám, dùng bác sĩ seed đã có tài khoản email `demo.staff03@example.test` cùng lịch được tạo bởi prepare_ui_demo.',
              'Nurse chọn khoa/ngày → Tải buổi khám → Chọn buổi. Khi khám, dùng đúng bác sĩ có tài khoản trên lượt khám.',
              'Các ngày đã qua có lịch để xem nhưng không đăng ký mới được. Sau 17:00 chọn ngày sau để đăng ký trước, chờ đúng buổi mới khám.',
              'Ngày ngoài khoảng trên không được bảo đảm lịch. Khoa xét nghiệm/dược/hành chính không có lịch khám.',
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
