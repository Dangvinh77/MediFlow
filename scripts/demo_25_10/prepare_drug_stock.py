"""Top up the demo pharmacy stock so a 3-month prescription can be dispensed.

New databases already get these levels from backend/pharmacy-service/.../demo-drugs.json; the seeder
never edits stock of an existing database, so this step raises older demo stores through the
audited stock-adjustment API (PHARMACIST). Each drug is topped up at most once per journal, and
only when it is below the target; a rerun never adds stock twice.
"""
import json
from pathlib import Path

from prepare_ui_demo import SEED_LOGIN_EMAILS

HERE = Path(__file__).resolve().parent
# 3 months x 3 tablets a day is 270 tablets per patient; thousands cover a full demo day.
TARGET_BY_UNIT = {'viên': 5000, 'gói': 1500, 'lọ': 1500}
DEFAULT_TARGET = 1000


def prepare(api):
    api.login('pharmacist', SEED_LOGIN_EMAILS['pharmacist'])
    drugs, page = [], 0
    while True:
        result = api.call('pharmacist', 'GET', f'/api/v1/pharmacy/drugs?page={page}&size=100')
        rows = result['content'] if isinstance(result, dict) else result
        drugs += rows
        page += 1
        if not isinstance(result, dict) or page >= result.get('totalPages', 1):
            break
    changed = []
    for drug in drugs:
        target = TARGET_BY_UNIT.get(drug.get('unit'), DEFAULT_TARGET)
        stage = f'drug-stock-{drug["drugId"]}-{target}'
        if stage in api.state or drug['stockQuantity'] >= target:
            continue
        delta = target - drug['stockQuantity']
        api.call('pharmacist', 'PUT', f'/api/v1/pharmacy/drugs/{drug["drugId"]}/stock',
                 {'quantity': delta, 'reason': 'Nhập kho demo: đủ kê đơn 3 tháng'}, stage=stage)
        changed.append({'drugName': drug['drugName'], 'added': delta, 'stock': target})
    print(f'DRUG STOCK READY: {len(changed)} of {len(drugs)} drugs topped up', flush=True)
    return changed


def main():
    from prepare_ui_demo import DemoApi, validate_manifest
    manifest = json.loads((HERE / 'evidence' / 'active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    # ASCII console output also works in the default Windows code page.
    for row in prepare(DemoApi(gateway, evidence / 'drug-stock-journal.local.json')):
        print(f'  {ascii(row["drugName"])}: +{row["added"]} -> {row["stock"]}', flush=True)


if __name__ == '__main__':
    main()
