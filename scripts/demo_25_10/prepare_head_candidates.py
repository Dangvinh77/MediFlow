"""Seed department-head candidates through the disposable Gateway.

Each active clinical department gets doctors in three account states, so the admin can
demonstrate who may be appointed head on the department detail screen:
  * ACTIVE  - an issued, active DOCTOR account: eligible;
  * LOCKED  - an issued account the admin then deactivated: refused with HEAD_INELIGIBLE;
  * the rotating roster doctors from prepare_demo_calendar have no account at all: refused too.
Uses public APIs and a resumable journal only. Accounts get the usual system-issued temporary
password, delivered to the local demo mailbox; nothing is sent to a real address.
"""
import json
from pathlib import Path
import uuid

HERE = Path(__file__).resolve().parent
ADMIN_LOGIN_EMAIL = 'demo.staff01@example.test'
# (suffix, display state, keep the account active)
CANDIDATES = (('TK01', 'Tài khoản hoạt động', True), ('TK02', 'Tài khoản hoạt động', True),
              ('KHOA', 'Tài khoản đã khóa', False))


def prepare(api):
    api.login('admin', ADMIN_LOGIN_EMAIL)
    departments = [row for row in api.call('admin', 'GET', '/api/v1/org/departments?activeOnly=true')
                   if row['active'] and row['departmentType'] == 'CLINICAL']
    result = []
    for department in departments:
        department_id = department['departmentId']
        token = uuid.uuid5(uuid.NAMESPACE_URL, 'mediflow-head-candidates/' + department_id).hex[:10]
        for index, (suffix, state, keep_active) in enumerate(CANDIDATES, 1):
            # Stable identity, so a rerun replays the same commands instead of adding doctors.
            identity = '97' + str(uuid.uuid5(uuid.NAMESPACE_URL, f'{department_id}/head/{index}').int % 10**10).zfill(10)
            email = f'head.{token}.{suffix.lower()}@example.test'
            full_name = f'BS Ứng viên {suffix} {department["departmentName"][:50]}'
            stage = f'head-candidate-{department_id}-{suffix}'
            staff = api.call('admin', 'POST', '/api/v1/org/staff', {
                'identityNumber': identity, 'fullName': full_name, 'departmentId': department_id,
                'jobTitle': 'DOCTOR', 'specialization': department['departmentName'][:100],
                'licenseNumber': f'DEMO-HEAD-{token}-{suffix}', 'phoneNumber': None, 'email': email,
            }, expected=(200, 201), stage=stage + '-staff')
            account = api.call('admin', 'POST', '/api/v1/org/accounts', {
                'username': email, 'staffId': staff['staffId'], 'patientId': None, 'role': 'DOCTOR',
            }, expected=(200, 201), stage=stage + '-account')
            if not keep_active:
                account = api.call('admin', 'PUT', f'/api/v1/org/accounts/{account["accountId"]}/status',
                                   {'isActive': False}, stage=stage + '-lock')
            if bool(account['isActive']) != keep_active:
                raise RuntimeError('Head candidate account state differs; reconcile: ' + full_name)
            result.append({'departmentName': department['departmentName'], 'fullName': full_name,
                           'email': email, 'state': state, 'eligible': keep_active})
        print(f'HEAD CANDIDATES PREPARED: department {department_id}', flush=True)
    return result


def write_fixtures(result, evidence):
    lines = ['# Ứng viên trưởng khoa', '',
             'Admin → Tổ chức → Khoa → chi tiết khoa lâm sàng → Bổ nhiệm trưởng khoa.',
             'Bác sĩ có tài khoản hoạt động bổ nhiệm được; tài khoản đã khóa hoặc chưa có tài khoản '
             '(các "BS Demo … 01–03" của lịch luân phiên) bị từ chối với thông báo chưa đủ điều kiện.', '',
             '| Khoa | Bác sĩ | Email đăng nhập | Trạng thái tài khoản |', '|---|---|---|---|']
    lines += [f'| {row["departmentName"]} | {row["fullName"]} | {row["email"]} | {row["state"]} |' for row in result]
    lines += ['', 'Mật khẩu tạm của tài khoản mới nằm trong hộp thư demo (/demo-mailbox); đăng nhập lần đầu phải đổi mật khẩu.']
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
