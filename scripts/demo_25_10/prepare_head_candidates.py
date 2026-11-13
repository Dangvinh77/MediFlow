"""Verify the seeded department-head candidates through the disposable Gateway.

Each active clinical department has doctors in three account states, so the admin can demonstrate
who may be appointed head on the department detail screen:
  * TK01 / TK02 - active DOCTOR account: eligible;
  * KHOA        - account that starts locked: refused with HEAD_INELIGIBLE;
  * the rotating roster doctors from prepare_demo_calendar have no account at all: refused too.
The candidates come from seed_head_candidates.sql (disposable demo) or demo-seed.json (local Core
seed) with the fixed demo password, so nothing is issued through the temporary-password email
path. This step only reads through the Gateway and writes the table.
"""
import json
from pathlib import Path
import urllib.parse

HERE = Path(__file__).resolve().parent
ADMIN_LOGIN_EMAIL = 'demo.staff01@example.test'
PREFIX = 'demo.head.'
STATES = {True: 'Tài khoản hoạt động', False: 'Tài khoản đã khóa'}


def pages(api, path, **query):
    page = 0
    while True:
        result = api.call('admin', 'GET', path + '?' + urllib.parse.urlencode({**query, 'page': page, 'size': 100}))
        yield from result['content']
        page += 1
        if page >= result['totalPages']:
            return


def prepare(api):
    api.login('admin', ADMIN_LOGIN_EMAIL)
    departments = {row['departmentId']: row for row in api.call('admin', 'GET', '/api/v1/org/departments?activeOnly=true')
                   if row['active'] and row['departmentType'] == 'CLINICAL'}
    candidates = sorted((row for row in pages(api, '/api/v1/org/staff', jobTitle='DOCTOR')
                         if (row.get('email') or '').lower().startswith(PREFIX) and row['departmentId'] in departments),
                        key=lambda row: (departments[row['departmentId']]['departmentName'], row['email']))
    missing = [row['departmentName'] for key, row in departments.items()
               if not any(c['departmentId'] == key for c in candidates)]
    if missing:
        raise RuntimeError('Seeded head candidates missing for: ' + ascii(missing))
    result = []
    for staff in candidates:
        account = api.call('admin', 'GET', f'/api/v1/org/profile-accounts/staff/{staff["staffId"]}')['account']
        active = bool(account and account['isActive'])
        expected = not staff['email'].lower().endswith('.khoa@example.test')
        if account is None or active != expected:
            # A changed state is an admin decision on this dataset; report it rather than reset it.
            print(f'HEAD CANDIDATE STATE CHANGED: {ascii(staff["fullName"])}', flush=True)
        result.append({'departmentName': departments[staff['departmentId']]['departmentName'],
                       'fullName': staff['fullName'], 'email': staff['email'],
                       'state': STATES[active] if account else 'Chưa cấp tài khoản', 'eligible': active})
    return result


def write_fixtures(result, evidence):
    lines = ['# Ứng viên trưởng khoa', '',
             'Admin → Tổ chức → Khoa → chi tiết khoa lâm sàng → Bổ nhiệm trưởng khoa.',
             'Bác sĩ có tài khoản hoạt động bổ nhiệm được; tài khoản đã khóa hoặc chưa có tài khoản '
             '(các "BS Demo … 01–03" của lịch luân phiên) bị từ chối với thông báo chưa đủ điều kiện.', '',
             '| Khoa | Bác sĩ | Email đăng nhập | Trạng thái tài khoản |', '|---|---|---|---|']
    lines += [f'| {row["departmentName"]} | {row["fullName"]} | {row["email"]} | {row["state"]} |' for row in result]
    lines += ['', 'Các tài khoản này được seed sẵn với mật khẩu mặc định của bộ dữ liệu demo (giống các tài khoản demo khác); '
              'không cấp qua hệ thống nên không có email gửi đi.']
    output = evidence / 'DEMO-HEAD-CANDIDATES.local.md'
    output.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'HEAD CANDIDATES READY: {len(result)} doctors; {output}', flush=True)


def main():
    from prepare_ui_demo import DemoApi, validate_manifest
    manifest = json.loads((HERE / 'evidence' / 'active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    write_fixtures(prepare(DemoApi(gateway, evidence / 'head-candidates-journal.local.json')), evidence)


if __name__ == '__main__':
    main()
