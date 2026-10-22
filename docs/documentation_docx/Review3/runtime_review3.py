"""Manage the local Review 3 demo using its private, machine-local launch manifest.

The first provisioned snapshot and launch manifest live in demo-runtime/, which
is ignored by Git. Never use this helper against a production environment.
"""
import argparse
import json
import os
import pathlib
import shutil
import subprocess
import time

FOLDER = pathlib.Path(__file__).resolve().parent
ROOT = FOLDER.parents[2]
PRIVATE = FOLDER / "demo-runtime"
MANIFEST = PRIVATE / "launch.json"
STATE = PRIVATE / "processes.json"


def running():
    output = subprocess.check_output([
        "powershell", "-NoProfile", "-Command",
        "@(Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | "
        "Select-Object ProcessId,CommandLine) | ConvertTo-Json -Compress"],
        encoding="utf-8-sig")
    values = json.loads(output or "[]")
    return values if isinstance(values, list) else [values]


def status(manifest):
    processes = running()
    for app in manifest:
        alive = next((p for p in processes if p["CommandLine"] == app["command"]), None)
        print(f"{app['name']}: {'running PID '+str(alive['ProcessId']) if alive else 'stopped'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "stop", "status", "backup"])
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if args.action == "status":
        status(manifest)
        return
    if args.action == "backup":
        subprocess.run(["docker", "exec", "mediflow-review3-postgres", "pg_dumpall", "-U", "test",
                        "--file=/tmp/review3-seeded.sql"], check=True)
        subprocess.run(["docker", "cp", "mediflow-review3-postgres:/tmp/review3-seeded.sql",
                        str(PRIVATE / "seeded.sql")], check=True)
        print("Saved demo-runtime/seeded.sql")
        return
    if args.action == "stop":
        # Match the full private launch command before stopping a PID, avoiding PID reuse.
        expected = {app["command"] for app in manifest}
        for process in running():
            if process["CommandLine"] in expected:
                subprocess.run(["taskkill", "/PID", str(process["ProcessId"]), "/F"], check=True)
        print("Stopped Review 3 application processes; database volumes are preserved.")
        return
    subprocess.run(["docker", "start", "mediflow-review3-postgres", "mediflow-review3-rabbitmq"], check=True)
    for container, command in [
        ("mediflow-review3-postgres", ["pg_isready", "-U", "test"]),
        ("mediflow-review3-rabbitmq", ["rabbitmq-diagnostics", "-q", "ping"])]:
        for attempt in range(60):
            result = subprocess.run(["docker", "exec", container, *command], capture_output=True)
            if result.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError(f"{container} did not become ready")
    active = {p["CommandLine"] for p in running()}
    catalog = ROOT / "backend/support-service/target/six-service-acceptance/billing-catalog.yml"
    if not catalog.exists():
        catalog.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(PRIVATE / "billing-catalog.yml", catalog)
    records = []
    for app in manifest:
        if app["command"] in active:
            print(f"{app['name']}: already running")
            continue
        environment = os.environ.copy()
        environment.update({
            "MEDIFLOW_JWT_SECRET": "clinical-packaged-acceptance-shared-secret-0123456789",
            "EUREKA_DEFAULT_ZONE": "http://127.0.0.1:58907/eureka/",
            "SPRING_DATASOURCE_USERNAME": "test", "SPRING_DATASOURCE_PASSWORD": "test",
            "MEDIFLOW_RABBIT_HOST": "127.0.0.1", "MEDIFLOW_RABBIT_PORT": "15673",
            "MEDIFLOW_RABBIT_USER": "guest", "MEDIFLOW_RABBIT_PASSWORD": "guest",
            "SPRING_RABBITMQ_HOST": "127.0.0.1", "SPRING_RABBITMQ_PORT": "15673",
            "SPRING_RABBITMQ_USERNAME": "guest", "SPRING_RABBITMQ_PASSWORD": "guest",
        })
        context = app['name'].removesuffix('-service')
        if context in ('clinical', 'lab', 'pharmacy', 'billing', 'support'):
            store = 'report' if context == 'support' else context
            environment['SPRING_DATASOURCE_URL'] = f'jdbc:postgresql://127.0.0.1:15432/mediflow_{store}'
        for store in ('ORGANIZATION', 'PATIENT', 'REPORT', 'NOTIFICATION'):
            environment.update({
                f'MEDIFLOW_{store}_DB_URL': f'jdbc:postgresql://127.0.0.1:15432/mediflow_{store.lower()}',
                f'MEDIFLOW_{store}_DB_USER': 'test', f'MEDIFLOW_{store}_DB_PASSWORD': 'test',
            })
        with (PRIVATE / f"{app['name']}.log").open("wb") as log:
            process = subprocess.Popen(app["command"], cwd=ROOT, stdout=log, stderr=log,
                                       env=environment, creationflags=subprocess.CREATE_NO_WINDOW)
        records.append({"name": app["name"], "pid": process.pid})
        print(f"Started {app['name']}")
    STATE.write_text(json.dumps(records, indent=2), encoding="utf-8")
    print("Gateway: http://127.0.0.1:58910 ; allow services to register before using the clients.")


if __name__ == "__main__":
    main()
