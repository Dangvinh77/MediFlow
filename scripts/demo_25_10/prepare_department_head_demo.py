"""Prepare a department-head and doctor-shift demo via an active disposable Gateway.

Run only after start-backend.bat reports READY. Uses public APIs and the rehearsal
journal, never business SQL; does not book a patient or write a medical result.
"""
import datetime as dt
import json
from pathlib import Path

from prepare_ui_demo import SEED_LOGIN_EMAILS

HERE = Path(__file__).resolve().parent
ZONE = dt.timezone(dt.timedelta(hours=7))


def prepare(api, now):
    api.login("admin", SEED_LOGIN_EMAILS["admin"])
    api.login("doctor", SEED_LOGIN_EMAILS["doctor"])
    doctor_id = api.claims["doctor"]["staffId"]
    doctor = api.call("admin", "GET", "/api/v1/org/staff/" + doctor_id)
    department_id = doctor["departmentId"]
    path = "/api/v2/org/departments/" + department_id + "/head-appointments"
    current = api.call("admin", "GET", path)
    if current.get("staffId") not in (None, doctor_id):
        raise RuntimeError("Department already has a different head; do not replace it automatically")
    if not current.get("staffId"):
        if "department-head-appointment" in api.state:
            raise RuntimeError("Previously prepared appointment is no longer active; reconcile the journal")
        current = api.call("admin", "PUT", path,
                           {"expectedRevision": current["revision"], "staffId": doctor_id,
                            "reason": "Bổ nhiệm trưởng khoa cho demo trên môi trường dùng một lần"},
                           stage="department-head-appointment")
    capabilities = api.call("doctor", "GET", "/api/v1/org/me/capabilities")
    if (capabilities.get("role") != "DOCTOR" or not capabilities.get("canPracticeAsDoctor")
            or not any(row["departmentId"] == department_id for row in capabilities["managementDepartments"])):
        raise RuntimeError("Doctor's current management authority is not ready")
    rooms = api.call("doctor", "GET", "/api/v2/org/consultation-rooms?departmentId=" + department_id)["content"]
    rooms = [room for room in rooms if room.get("roomName") != "Phong kham lich day demo"]
    if not rooms:
        raise RuntimeError("No eligible consultation room")
    shift_date = str((now.astimezone(ZONE).date() + dt.timedelta(days=2)))
    # Keep the original date across reruns, just as command bodies/keys are retained.
    previous = api.state.get("department-head-work-shift")
    if previous:
        shift_date = previous["body"]["shiftDate"]
    shift_body = {"doctorId": doctor_id, "departmentId": department_id, "roomId": rooms[0]["roomId"],
                  "shiftDate": shift_date, "startTime": "08:00", "endTime": "12:00"}
    shift = api.call("doctor", "POST", "/api/v2/doctor-work-shifts", shift_body,
                     expected=(201,), stage="department-head-work-shift")
    session_body = {"doctorId": doctor_id, "departmentId": department_id, "roomId": shift["roomId"],
                    "sessionDate": shift_date, "startTime": "09:00", "endTime": "11:00",
                    "breaks": [], "capacity": 12}
    session = api.call("doctor", "POST", "/api/v2/doctor-work-shifts/" + shift["shiftId"] + "/consultation-sessions",
                       session_body, expected=(201,), stage="department-head-shift-session")
    return {"doctor": doctor, "appointment": current, "capabilities": capabilities,
            "shift": shift, "session": session}


def verify_revocation(api, fixture):
    """Exercise revocation with the SAME existing doctor access token, then restore demo."""
    department_id = fixture["doctor"]["departmentId"]
    path = "/api/v2/org/departments/" + department_id + "/head-appointments"
    current = api.call("admin", "GET", path)
    original_token = api.tokens["doctor"]
    revoked = api.call("admin", "PUT", path,
                       {"expectedRevision": current["revision"], "staffId": None,
                        "reason": "Kiểm chứng miễn nhiệm: giữ phiên bác sĩ cũ"},
                       stage="department-head-proof-revoke")
    capabilities = api.call("doctor", "GET", "/api/v1/org/me/capabilities")
    if capabilities["role"] != "DOCTOR" or not capabilities["canPracticeAsDoctor"] or capabilities["managementDepartments"]:
        raise RuntimeError("Revocation changed professional authority or retained management grant")
    shift = fixture["shift"]
    query = "?departmentId=" + department_id + "&fromDate=" + shift["shiftDate"] + "&toDate=" + shift["shiftDate"]
    api.call("doctor", "GET", "/api/v2/doctor-work-shifts" + query, expected=(403,))
    api.call("doctor", "GET", "/api/v2/consultation-sessions" + query, expected=(403,))
    api.call("doctor", "GET", "/api/v2/doctor-work-shifts/mine?fromDate=" + shift["shiftDate"] + "&toDate=" + shift["shiftDate"])
    api.call("doctor", "GET", "/api/v2/consultation-sessions/mine?date=" + shift["shiftDate"])
    restored = api.call("admin", "PUT", path,
                        {"expectedRevision": revoked["revision"], "staffId": fixture["doctor"]["staffId"],
                         "reason": "Khôi phục bổ nhiệm để tiếp tục demo UI"},
                        stage="department-head-proof-restore")
    final = api.call("doctor", "GET", "/api/v1/org/me/capabilities")
    if api.tokens["doctor"] != original_token or not final["managementDepartments"]:
        raise RuntimeError("Same-token restore verification failed")
    return {"sameToken": True, "professionalAuthorityPreserved": True,
            "revokedDepartmentReadsDenied": True, "ownSchedulesRemainReadable": True,
            "restoredRevision": restored["revision"]}


def main():
    import argparse
    from prepare_ui_demo import DemoApi, validate_manifest
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-revocation", action="store_true",
                        help="Revoke/restore this disposable fixture and verify old-token denial")
    options = parser.parse_args()
    manifest = json.loads((HERE / "evidence" / "active-rehearsal.local.json").read_text(encoding="utf-8"))
    gateway, evidence = validate_manifest(manifest)
    api = DemoApi(gateway, evidence / "department-head-preparation.json")
    result = prepare(api, dt.datetime.now(dt.timezone.utc))
    output = evidence / "department-head-demo.json"
    previous = json.loads(output.read_text(encoding="utf-8")) if output.exists() else {}
    proof = previous.get("revocationProof")
    same_fixture = previous.get("shift", {}).get("shiftId") == result["shift"]["shiftId"]
    if proof and same_fixture and proof.get("restoredRevision") == result["appointment"]["revision"]:
        result["revocationProof"] = proof  # Retain completed proof on an idempotent rerun.
    elif options.verify_revocation:
        result["revocationProof"] = verify_revocation(api, result)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("DEPARTMENT HEAD READY: " + SEED_LOGIN_EMAILS["doctor"] + " retains DOCTOR and can manage its department", flush=True)
    print("Sample date: " + result["shift"]["shiftDate"] + "; shift 08:00-12:00; consultation 09:00-11:00; capacity 12", flush=True)
    print("No patient registered in the sample session. IDs and evidence are in the local rehearsal folder.", flush=True)


if __name__ == "__main__":
    main()
