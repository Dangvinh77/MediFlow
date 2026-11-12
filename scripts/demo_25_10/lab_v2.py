"""Lab v2 steps shared by the demo scripts: catalogue order and second-person release.

With the Lab catalogue on, Clinical refuses free-text orders, and a catalogue test can only be completed
by a report one lab tech writes and another releases (seed: demo.lab, then demo.lab2).
"""

# Lab requires the catalogue name, as the web order form sends it. Values: first release, then amendment.
SERVICES = {
    'GLU': {'name': 'Glucose máu', 'price': 'LAB-GLU', 'specimen': 'SERUM',
            'values': {'GLU': '27.5'}, 'amended': {'GLU': '7.1'}},
    'CBC': {'name': 'Công thức máu', 'price': 'LAB-CBC', 'specimen': 'WHOLE_BLOOD_EDTA',
            'values': {'WBC': '7.0', 'RBC': '4.8', 'HGB': '140', 'HCT': '0.42', 'PLT': '250'},
            'amended': {'WBC': '6.5', 'RBC': '4.8', 'HGB': '140', 'HCT': '0.42', 'PLT': '250'}},
}


def values(mapping):
    return [{'fieldCode': code, 'value': value} for code, value in mapping.items()]


def order(api, record_id, patient_id, department_id, registration_id, day, stage, code='CBC'):
    service = SERVICES[code]
    choice = {'labType': service['name'], 'requestedDate': day, 'priceCode': service['price'], 'serviceCode': code}
    source = api.call('doctor', 'POST', '/api/v1/records/' + record_id + '/lab-orders', choice,
                      expected=(201,), stage=stage + '-order')
    return api.call('doctor', 'POST', '/api/v1/lab', {
        **choice, 'recordId': record_id, 'patientId': patient_id, 'requestingDepartmentId': department_id,
        'sourceOrderId': source['sourceOrderId'], 'careEpisodeType': 'OUTPATIENT_VISIT',
        'careEpisodeId': registration_id}, expected=(201,), stage=stage + '-test')


def accept_specimen(api, test_id, code='CBC'):
    specimens = api.call('lab', 'GET', '/api/v2/lab/' + test_id + '/specimens')
    if any(row.get('decision') == 'ACCEPTED' for row in specimens):
        return
    specimen = api.call('lab', 'POST', '/api/v2/lab/' + test_id + '/specimens',
                        {'specimenType': SERVICES[code]['specimen']}, expected=(200, 201))
    api.call('lab', 'PUT', '/api/v2/lab/specimens/' + specimen['specimenId'] + '/decision', {'decision': 'ACCEPTED'})


def submit(api, test_id, day, conclusion, mapping):
    """Writes and submits as 'lab'; returns the report (unchanged when it is already past DRAFT)."""
    # 404 until the first draft: both demo clients return no data for an expected error.
    report = api.call('lab', 'GET', '/api/v2/lab/' + test_id + '/report', expected=(200, 404))
    report = report if isinstance(report, dict) and 'status' in report else None
    if report is not None and report['status'] != 'DRAFT':
        return report
    report = api.call('lab', 'PUT', '/api/v2/lab/' + test_id + '/report/draft', {
        'draftRevision': report['draftRevision'] if report else 0, 'values': values(mapping),
        'conclusion': conclusion, 'performedDate': day})
    return api.call('lab', 'POST', '/api/v2/lab/' + test_id + '/report/submission',
                    {'draftRevision': report['draftRevision']}, expected=(200, 201))


def release(api, test_id, day, code='CBC', conclusion='Ket qua mo phong phuc vu demo'):
    """Specimen, draft and submit by 'lab', release by 'lab2'. The test must already be IN_PROGRESS."""
    accept_specimen(api, test_id, code)
    report = submit(api, test_id, day, conclusion, SERVICES[code]['values'])
    if report['status'] == 'PENDING_REVIEW':
        report = api.call('lab2', 'POST', '/api/v2/lab/' + test_id + '/report/release', expected=(200, 201))
    return report
