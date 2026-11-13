"""Seed every presenter's demo state on the running backend (start-backend.bat runs this too).

Order: prepare_ui_demo (schedules, follow-up source) -> prepare_cashier_demo (Lộc's open charges)
-> prepare_staged_demo (one patient per hand-off for Vinh and Huy). Every step resumes from its
journal, so running it again the same day never creates a second visit.

Started outside clinic hours (07:00-16:45 Vietnam), only schedules are prepared. Run
scripts/run/seed-demo.bat once the clinic opens; no backend restart is needed.

    python scripts/demo_25_10/seed_demo.py                   # all seeds
    python scripts/demo_25_10/seed_demo.py --exam-charge-now # Lộc's unpaid exam fee (15-minute hold)
"""
import argparse
import datetime as dt
import json
import urllib.request


def prepare_seeds(gateway, evidence, cancel_file=None):
    """Run the three seeds in order and return the UI fixtures (the calendar seed needs them)."""
    from prepare_ui_demo import ZONE, DemoApi, prepare, write_fixtures
    fixtures = prepare(DemoApi(gateway, evidence / 'ui-demo-journal.local.json', cancel_file), dt.datetime.now(ZONE))
    write_fixtures(fixtures, evidence)

    from prepare_cashier_demo import prepare as prepare_cashier, write_fixtures as write_cashier
    cashier_api = DemoApi(gateway, evidence / 'cashier-journal.local.json', cancel_file)
    write_cashier(prepare_cashier(cashier_api, fixtures, dt.datetime.now(ZONE)), evidence)

    # Thousands of units per drug, so a 3-month prescription can be dispensed in the demo.
    from prepare_drug_stock import prepare as prepare_stock
    prepare_stock(DemoApi(gateway, evidence / 'drug-stock-journal.local.json', cancel_file))

    # One patient per presenter hand-off; a failed case is reported, never blocks READY.
    from prepare_staged_demo import prepare as prepare_staged, write_fixtures as write_staged
    staged_api = DemoApi(gateway, evidence / 'staged-journal.local.json', cancel_file)
    try:
        write_staged(prepare_staged(staged_api, fixtures, dt.datetime.now(ZONE)), evidence)
    except InterruptedError:
        raise
    except Exception as error:
        print('STAGED DEMO PARTIAL: ' + str(error), flush=True)
    return fixtures


def active_backend():
    from prepare_ui_demo import HERE, validate_manifest
    manifest = json.loads((HERE / 'evidence/active-rehearsal.local.json').read_text(encoding='utf-8'))
    gateway, evidence = validate_manifest(manifest)
    try:
        with urllib.request.urlopen(gateway + '/actuator/health', timeout=5) as response:
            healthy = json.loads(response.read().decode('utf-8')).get('status') == 'UP'
    except OSError:
        healthy = False
    if not healthy:
        raise SystemExit('Gateway 28080 is not ready. Run start-backend.bat first and wait for READY.')
    return gateway, evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--exam-charge-now', action='store_true',
                        help="Create Lộc's unpaid exam-fee registration now (holds its slot two hours).")
    options = parser.parse_args()
    from prepare_ui_demo import ZONE, DemoApi
    gateway, evidence = active_backend()
    if options.exam_charge_now:
        from prepare_staged_demo import exam_charge_now
        fixtures = json.loads((evidence / 'ui-demo-fixtures.local.json').read_text(encoding='utf-8'))
        api = DemoApi(gateway, evidence / 'staged-journal.local.json')
        result = exam_charge_now(api, fixtures, dt.datetime.now(ZONE))
        print(f'EXAM CHARGE READY: {result["hoTen"]} (CCCD {result["cccd"]}) {result["status"]}, '
              f'hết hạn giữ suất {result["reservationExpiresAt"]}', flush=True)
        return
    prepare_seeds(gateway, evidence)
    print('Bảng bệnh nhân theo người demo: ' + str(evidence / 'DEMO-STAGED.local.md'), flush=True)


if __name__ == '__main__':
    main()
