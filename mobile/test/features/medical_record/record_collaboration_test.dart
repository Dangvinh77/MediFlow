import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_record_collaboration_repository.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/domain/record_collaboration.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/record_collaboration_section.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/treatment_courses_section.dart';

const _patient = '20000000-0000-4000-8000-000000000001';
const _record = '20000000-0000-4000-8000-000000000002';
const _me = '20000000-0000-4000-8000-000000000003';
const _other = '20000000-0000-4000-8000-000000000004';
const _course = '20000000-0000-4000-8000-000000000005';
const _handover = '20000000-0000-4000-8000-000000000006';
const _amendment = '20000000-0000-4000-8000-000000000007';
const _department = '20000000-0000-4000-8000-000000000008';
const _hash =
    'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789';

TreatmentCourse _courseModel({
  String status = 'OPEN',
  bool reviewDue = false,
  List<String> recordIds = const [_other],
}) => TreatmentCourse(
  courseId: _course,
  patientId: _patient,
  departmentId: _department,
  problem: 'Tăng huyết áp',
  openingReason: 'Theo dõi dài hạn',
  responsibleDoctorId: _me,
  status: status,
  recordIds: recordIds,
  openedAt: '2026-11-01T01:00:00Z',
  lastVisitDate: '2026-11-10',
  reviewDue: reviewDue,
);

RecordHandover _handoverModel({
  String status = 'PENDING',
  String from = _other,
  String to = _me,
}) => RecordHandover(
  handoverId: _handover,
  recordId: _record,
  fromDoctorId: from,
  toDoctorId: to,
  reason: 'Hết ca trực',
  pendingItems: 'Chờ kết quả xét nghiệm',
  status: status,
  requestedAt: '2026-11-10T01:00:00Z',
);

RecordAmendment _amendmentModel({
  String status = 'PENDING',
  String author = _other,
  String? hash,
}) => RecordAmendment(
  amendmentId: _amendment,
  recordId: _record,
  section: 'ADVICE',
  originalText: 'Uống thuốc sau ăn',
  amendedText: 'Uống thuốc trước ăn',
  reason: 'Ghi nhầm',
  authorStaffId: author,
  status: status,
  requestedAt: '2026-11-10T02:00:00Z',
  contentHash: hash,
);

class CollabFake implements RecordCollaborationRepository {
  Future<List<TreatmentCourse>> Function()? onCourses;
  Future<List<RecordHandover>> Function()? onHandovers;
  Future<List<ConsultationNote>> Function()? onNotes;
  Future<List<RecordAmendment>> Function()? onAmendments;
  Future<List<RecordAmendment>> Function()? onPending;
  Future<void> Function()? onCommand;
  int reads = 0;
  final commands = <String>[];
  final keys = <String>[];
  final payloads = <Map<String, Object?>>[];

  Future<void> _cmd(String name, String key, Map<String, Object?> payload) {
    commands.add(name);
    keys.add(key);
    payloads.add(payload);
    return onCommand?.call() ?? Future.value();
  }

  @override
  Future<List<TreatmentCourse>> courses(String patientId) {
    reads++;
    return onCourses?.call() ?? Future.value(const []);
  }

  @override
  Future<void> openCourse({
    required String patientId,
    required String problem,
    required String openingReason,
    required String recordId,
    required String idempotencyKey,
  }) => _cmd('openCourse', idempotencyKey, {
    'patientId': patientId,
    'problem': problem,
    'openingReason': openingReason,
    'recordId': recordId,
  });

  @override
  Future<void> attachVisit({
    required String courseId,
    required String recordId,
    required String idempotencyKey,
  }) => _cmd('attachVisit', idempotencyKey, {
    'courseId': courseId,
    'recordId': recordId,
  });

  @override
  Future<void> closeCourse({
    required String courseId,
    required String decision,
    required String note,
    required String idempotencyKey,
  }) => _cmd('closeCourse', idempotencyKey, {
    'courseId': courseId,
    'decision': decision,
    'note': note,
  });

  @override
  Future<List<RecordHandover>> handovers(String recordId) {
    reads++;
    return onHandovers?.call() ?? Future.value(const []);
  }

  @override
  Future<void> requestHandover(
    String recordId, {
    required String toDoctorId,
    required String reason,
    required String pendingItems,
    required String idempotencyKey,
  }) => _cmd('requestHandover', idempotencyKey, {
    'toDoctorId': toDoctorId,
    'reason': reason,
    'pendingItems': pendingItems,
  });

  @override
  Future<void> acceptHandover(
    String recordId,
    String handoverId, {
    required String idempotencyKey,
  }) => _cmd('acceptHandover', idempotencyKey, {'handoverId': handoverId});

  @override
  Future<List<ConsultationNote>> notes(String recordId) {
    reads++;
    return onNotes?.call() ?? Future.value(const []);
  }

  @override
  Future<void> addNote(
    String recordId, {
    required String opinion,
    required String idempotencyKey,
  }) => _cmd('addNote', idempotencyKey, {'opinion': opinion});

  @override
  Future<List<RecordAmendment>> amendments(String recordId) {
    reads++;
    return onAmendments?.call() ?? Future.value(const []);
  }

  @override
  Future<void> requestAmendment(
    String recordId, {
    required String section,
    required String amendedText,
    required String reason,
    required String idempotencyKey,
  }) => _cmd('requestAmendment', idempotencyKey, {
    'section': section,
    'amendedText': amendedText,
    'reason': reason,
  });

  @override
  Future<void> decideAmendment(
    String recordId,
    String amendmentId, {
    required String decision,
    required String note,
    required String idempotencyKey,
  }) => _cmd('decideAmendment', idempotencyKey, {
    'amendmentId': amendmentId,
    'decision': decision,
    'note': note,
  });

  @override
  Future<List<RecordAmendment>> pendingAmendments() {
    reads++;
    return onPending?.call() ?? Future.value(const []);
  }
}

RecordCapabilities _caps({
  bool? handover,
  bool? accept,
  bool? note,
  bool? amend,
  bool? decide,
}) => RecordCapabilities(
  recordId: _record,
  patientId: _patient,
  canRead: true,
  canWriteContent: false,
  canComplete: false,
  canHandover: handover,
  canAcceptHandover: accept,
  canAddConsultationNote: note,
  canAmend: amend,
  canDecideAmendments: decide,
);

Future<void> _pump(
  WidgetTester tester,
  CollabFake repository, {
  RecordCapabilities? capabilities,
  bool courses = false,
  String? staffId = _me,
}) async {
  await tester.binding.setSurfaceSize(const Size(900, 4000));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    ProviderScope(
      key: UniqueKey(),
      overrides: [
        recordCollaborationRepositoryProvider.overrideWithValue(repository),
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(
            role: 'DOCTOR',
            sessionIdentity: 'session-one',
            staffId: staffId,
          ),
        ),
      ],
      child: MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: [
                if (courses)
                  const TreatmentCoursesSection(
                    patientId: _patient,
                    recordId: _record,
                  ),
                RecordCollaborationSection(
                  recordId: _record,
                  capabilities: capabilities,
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _type(WidgetTester tester, String key, String text) async {
  await tester.enterText(find.byKey(Key(key)), text);
  await tester.pump();
}

Future<void> _tap(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
}

Future<void> _choose(WidgetTester tester, String key, String label) async {
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
  await tester.tap(find.text(label).last);
  await tester.pumpAndSettle();
}

class _Transport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? data;
  int status = 200;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(
        status == 200
            ? {'success': true, 'data': data}
            : {
                'success': false,
                'error': {'code': 'NOT_FOUND', 'message': 'Không có'},
              },
      ),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

ApiClient _client(_Transport transport) => ApiClient(
  AppConfig('https://gateway.invalid/api'),
  dio: Dio()..httpClientAdapter = transport,
);

void main() {
  group('treatment courses', () {
    testWidgets('coursesHiddenWhenFeatureOff', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => throw const CollaborationUnavailable();
      await _pump(tester, repository, courses: true);
      expect(find.text('Đợt điều trị'), findsNothing);
      expect(find.textContaining('Không tải được'), findsNothing);
    });

    testWidgets('openCourseFromVisit', (tester) async {
      final repository = CollabFake();
      await _pump(tester, repository, courses: true);
      expect(find.text('Chưa có đợt điều trị.'), findsOneWidget);
      await _tap(tester, 'toggle-course-open');
      await _type(tester, 'cf-course-open-problem', 'Tăng huyết áp');
      await _type(tester, 'cf-course-open-openingReason', 'Theo dõi dài hạn');
      await _tap(tester, 'cf-course-open-submit');
      expect(repository.commands, ['openCourse']);
      expect(repository.payloads.single, {
        'patientId': _patient,
        'problem': 'Tăng huyết áp',
        'openingReason': 'Theo dõi dài hạn',
        'recordId': _record,
      });
      expect(repository.keys.single, isNotEmpty);
    });

    testWidgets('addVisitToCourse', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => [_courseModel()];
      await _pump(tester, repository, courses: true);
      expect(find.text('Tăng huyết áp'), findsOneWidget);
      await _tap(tester, 'course-attach-$_course');
      expect(repository.payloads.single, {
        'courseId': _course,
        'recordId': _record,
      });
    });

    testWidgets('visit already in the course offers no attach action', (
      tester,
    ) async {
      final repository = CollabFake()
        ..onCourses = () async => [
          _courseModel(recordIds: const [_record]),
        ];
      await _pump(tester, repository, courses: true);
      expect(find.byKey(const Key('course-attach-$_course')), findsNothing);
    });

    testWidgets('reviewDueIsShown', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => [_courseModel(reviewDue: true)];
      await _pump(tester, repository, courses: true);
      expect(find.text('Cần rà soát'), findsOneWidget);
    });

    testWidgets('closeCourseNeedsDecisionAndNote', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => [_courseModel()];
      await _pump(tester, repository, courses: true);
      await _tap(tester, 'course-close-$_course');
      await _tap(tester, 'cf-course-close-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Chọn kết luận của đợt điều trị.'), findsOneWidget);
      await _choose(tester, 'cf-course-close-decision', 'Đã giải quyết');
      await _tap(tester, 'cf-course-close-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập ghi chú kết thúc đợt điều trị.'), findsOneWidget);
      await _type(tester, 'cf-course-close-note', 'Huyết áp ổn định');
      await _tap(tester, 'cf-course-close-submit');
      expect(repository.payloads.single, {
        'courseId': _course,
        'decision': 'RESOLVED',
        'note': 'Huyết áp ổn định',
      });
    });

    testWidgets('closed course has no write actions', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => [_courseModel(status: 'CLOSED')];
      await _pump(tester, repository, courses: true);
      expect(find.byKey(const Key('course-close-$_course')), findsNothing);
      expect(find.byKey(const Key('course-attach-$_course')), findsNothing);
    });

    testWidgets('courses outage is an error, never empty', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => throw const ApiFailure('Mất kết nối');
      await _pump(tester, repository, courses: true);
      expect(
        find.textContaining('Không tải được đợt điều trị'),
        findsOneWidget,
      );
      expect(find.text('Chưa có đợt điều trị.'), findsNothing);
      expect(find.byKey(const Key('retry-courses')), findsOneWidget);
    });

    testWidgets('courses 403 is a no-permission state', (tester) async {
      final repository = CollabFake()
        ..onCourses = () async => throw const ApiFailure('x', status: 403);
      await _pump(tester, repository, courses: true);
      expect(
        find.text('Bạn không có quyền xem đợt điều trị của bệnh nhân này.'),
        findsOneWidget,
      );
      expect(find.text('Chưa có đợt điều trị.'), findsNothing);
    });
  });

  group('handover', () {
    testWidgets('handoverNeedsReceiverReasonAndPendingItems', (tester) async {
      final repository = CollabFake();
      await _pump(tester, repository, capabilities: _caps(handover: true));
      await _tap(tester, 'toggle-handover');
      await _tap(tester, 'cf-handover-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập mã bác sĩ nhận bàn giao.'), findsOneWidget);
      await _type(tester, 'cf-handover-toDoctorId', _other);
      await _type(tester, 'cf-handover-reason', 'Hết ca trực');
      await _tap(tester, 'cf-handover-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập các việc còn dang dở.'), findsOneWidget);
      await _type(tester, 'cf-handover-pendingItems', 'Chờ kết quả xét nghiệm');
      await _tap(tester, 'cf-handover-submit');
      expect(repository.payloads.single, {
        'toDoctorId': _other,
        'reason': 'Hết ca trực',
        'pendingItems': 'Chờ kết quả xét nghiệm',
      });
    });

    testWidgets('handover reason and pending items have length limits', (
      tester,
    ) async {
      await _pump(
        tester,
        CollabFake(),
        capabilities: _caps(handover: true),
      );
      await _tap(tester, 'toggle-handover');
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('cf-handover-reason')))
            .maxLength,
        500,
      );
      expect(
        tester
            .widget<TextField>(
              find.byKey(const Key('cf-handover-pendingItems')),
            )
            .maxLength,
        2000,
      );
    });

    testWidgets('receiverAcceptsHandover', (tester) async {
      final repository = CollabFake()
        ..onHandovers = () async => [_handoverModel()];
      await _pump(tester, repository, capabilities: _caps(accept: true));
      expect(find.text('Hết ca trực'), findsOneWidget);
      await _tap(tester, 'handover-accept-$_handover');
      expect(repository.commands, ['acceptHandover']);
      expect(repository.keys.single, isNotEmpty);
    });

    testWidgets('only the named receiver may accept', (tester) async {
      final repository = CollabFake()
        ..onHandovers = () async => [_handoverModel(to: _other)];
      await _pump(tester, repository, capabilities: _caps(accept: true));
      expect(find.byKey(const Key('handover-accept-$_handover')), findsNothing);
    });

    testWidgets('formerDoctorSeesReadOnlyAfterHandover', (tester) async {
      final repository = CollabFake()
        ..onHandovers = (() async => [
          _handoverModel(status: 'ACCEPTED', from: _me, to: _other),
        ])
        ..onAmendments = () async => [_amendmentModel(author: _other)];
      await _pump(
        tester,
        repository,
        capabilities: _caps(
          handover: true,
          note: true,
          amend: true,
          decide: true,
        ),
      );
      expect(
        find.text('Hồ sơ đã bàn giao cho bác sĩ khác. Bạn chỉ có thể xem.'),
        findsOneWidget,
      );
      expect(find.byKey(const Key('toggle-handover')), findsNothing);
      expect(find.byKey(const Key('toggle-note')), findsNothing);
      expect(find.byKey(const Key('toggle-amend')), findsNothing);
      expect(
        find.byKey(const Key('amendment-decide-$_amendment')),
        findsNothing,
      );
    });
  });

  group('consultation notes', () {
    testWidgets('notesShowAuthorAndTime oldest first', (tester) async {
      final repository = CollabFake()
        ..onNotes = () async => [
          const ConsultationNote(
            noteId: 'b',
            recordId: _record,
            authorStaffId: _other,
            opinion: 'Ý kiến sau',
            createdAt: '2026-11-10T10:00:00Z',
          ),
          const ConsultationNote(
            noteId: 'a',
            recordId: _record,
            authorStaffId: _me,
            opinion: 'Ý kiến trước',
            createdAt: '2026-11-10T03:00:00Z',
          ),
        ];
      await _pump(tester, repository, capabilities: _caps(note: false));
      expect(find.textContaining('2026-11-10 17:00 (GMT+7)'), findsOneWidget);
      expect(find.textContaining(_other), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Ý kiến trước')).dy,
        lessThan(tester.getTopLeft(find.text('Ý kiến sau')).dy),
      );
    });

    testWidgets('noteFormOnlyWithCapability', (tester) async {
      final repository = CollabFake();
      await _pump(tester, repository, capabilities: _caps(note: false));
      expect(find.byKey(const Key('toggle-note')), findsNothing);
      await _pump(tester, repository, capabilities: _caps(note: true));
      expect(find.byKey(const Key('toggle-note')), findsOneWidget);
    });

    testWidgets('note validates 1..4000 and sends opinion', (tester) async {
      final repository = CollabFake();
      await _pump(tester, repository, capabilities: _caps(note: true));
      await _tap(tester, 'toggle-note');
      await _tap(tester, 'cf-note-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập ý kiến hội chẩn.'), findsOneWidget);
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('cf-note-opinion')))
            .maxLength,
        4000,
      );
      await _type(tester, 'cf-note-opinion', 'Đồng ý hướng xử trí');
      await _tap(tester, 'cf-note-submit');
      expect(repository.payloads.single, {'opinion': 'Đồng ý hướng xử trí'});
    });

    testWidgets('notes outage shows an error, not empty', (tester) async {
      final repository = CollabFake()
        ..onNotes = () async => throw const ApiFailure('Mất kết nối');
      await _pump(tester, repository, capabilities: _caps(note: true));
      expect(
        find.textContaining('Không tải được ý kiến hội chẩn'),
        findsOneWidget,
      );
      expect(find.text('Chưa có ý kiến hội chẩn.'), findsNothing);
    });
  });

  group('amendments', () {
    testWidgets('amendmentShowsOriginalBesideAmended', (tester) async {
      final repository = CollabFake()
        ..onAmendments = () async => [_amendmentModel()];
      await _pump(tester, repository, capabilities: _caps(amend: true));
      final original = tester.getTopLeft(find.text('Uống thuốc sau ăn'));
      final amended = tester.getTopLeft(find.text('Uống thuốc trước ăn'));
      expect(original.dy, amended.dy);
      expect(original.dx, lessThan(amended.dx));
      expect(find.text('Ghi nhầm'), findsOneWidget);
    });

    testWidgets('amendmentNeedsReasonAndText', (tester) async {
      final repository = CollabFake();
      await _pump(tester, repository, capabilities: _caps(amend: true));
      await _tap(tester, 'toggle-amend');
      await _tap(tester, 'cf-amend-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập nội dung đính chính.'), findsOneWidget);
      await _type(tester, 'cf-amend-amendedText', 'Uống thuốc trước ăn');
      await _tap(tester, 'cf-amend-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập lý do đính chính.'), findsOneWidget);
      await _type(tester, 'cf-amend-reason', 'Ghi nhầm');
      await _choose(tester, 'cf-amend-section', 'Dặn dò');
      await _tap(tester, 'cf-amend-submit');
      expect(repository.payloads.single, {
        'section': 'ADVICE',
        'amendedText': 'Uống thuốc trước ăn',
        'reason': 'Ghi nhầm',
      });
    });

    testWidgets('headDecidesAmendment', (tester) async {
      final repository = CollabFake()
        ..onAmendments = () async => [_amendmentModel()];
      await _pump(tester, repository, capabilities: _caps(decide: true));
      await _tap(tester, 'amendment-decide-$_amendment');
      await _choose(tester, 'cf-decide-decision', 'Từ chối');
      await _tap(tester, 'cf-decide-submit');
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập ghi chú khi từ chối đính chính.'), findsOneWidget);
      await _choose(tester, 'cf-decide-decision', 'Duyệt');
      await _tap(tester, 'cf-decide-submit');
      expect(repository.payloads.single, {
        'amendmentId': _amendment,
        'decision': 'APPROVED',
        'note': '',
      });
    });

    testWidgets('authorSeesNoDecisionButtons', (tester) async {
      final repository = CollabFake()
        ..onAmendments = () async => [_amendmentModel(author: _me)];
      await _pump(tester, repository, capabilities: _caps(decide: true));
      expect(
        find.byKey(const Key('amendment-decide-$_amendment')),
        findsNothing,
      );
    });

    testWidgets('approvedAmendmentShowsInternalConfirmation', (tester) async {
      final repository = CollabFake()
        ..onAmendments = () async => [
          _amendmentModel(status: 'APPROVED', hash: _hash),
        ];
      await _pump(tester, repository, capabilities: _caps(amend: true));
      expect(find.text('Đã xác nhận nội bộ'), findsOneWidget);
      expect(find.textContaining(_hash.substring(0, 12)), findsOneWidget);
      expect(find.textContaining(_hash.substring(0, 13)), findsNothing);
      for (final banned in ['chữ ký', 'ký số', 'hợp pháp']) {
        expect(find.textContaining(banned), findsNothing);
      }
    });

    testWidgets('pending queue is shown to reviewers only', (tester) async {
      final repository = CollabFake()
        ..onPending = () async => [_amendmentModel()];
      await _pump(tester, repository, capabilities: _caps(decide: true));
      expect(find.text('Hàng chờ duyệt đính chính'), findsOneWidget);
      await _pump(tester, repository, capabilities: _caps(amend: true));
      expect(find.text('Hàng chờ duyệt đính chính'), findsNothing);
    });
  });

  group('availability and idempotency', () {
    testWidgets('featureOffHidesSections', (tester) async {
      final repository = CollabFake();
      await _pump(tester, repository, capabilities: _caps());
      expect(find.text('Bàn giao hồ sơ'), findsNothing);
      expect(find.text('Ý kiến hội chẩn'), findsNothing);
      expect(find.text('Đính chính hồ sơ'), findsNothing);
      expect(find.text('Hàng chờ duyệt đính chính'), findsNothing);
      expect(repository.reads, 0);
      await _pump(tester, repository, capabilities: null);
      expect(find.text('Bàn giao hồ sơ'), findsNothing);
      expect(repository.reads, 0);
    });

    testWidgets('uncertainOutcomeKeepsKeyForRetry', (tester) async {
      var attempts = 0;
      final repository = CollabFake()
        ..onCommand = () async {
          if (attempts++ == 0) throw const ApiFailure('Mất kết nối');
        };
      await _pump(tester, repository, capabilities: _caps(note: true));
      await _tap(tester, 'toggle-note');
      await _type(tester, 'cf-note-opinion', 'Đồng ý');
      await _tap(tester, 'cf-note-submit');
      expect(find.textContaining('Kết quả chưa rõ'), findsOneWidget);
      await _tap(tester, 'cf-note-submit');
      expect(repository.keys, hasLength(2));
      expect(repository.keys[0], repository.keys[1]);
      expect(repository.payloads[0], repository.payloads[1]);
    });

    testWidgets('a definite refusal shows a mapped message and frees the key', (
      tester,
    ) async {
      var attempts = 0;
      final repository = CollabFake()
        ..onCommand = () async {
          if (attempts++ == 0) {
            throw const ApiFailure(
              'raw',
              status: 409,
              code: 'AMENDMENT_SELF_DECISION',
            );
          }
        };
      await _pump(tester, repository, capabilities: _caps(note: true));
      await _tap(tester, 'toggle-note');
      await _type(tester, 'cf-note-opinion', 'Đồng ý');
      await _tap(tester, 'cf-note-submit');
      expect(
        find.text('Người đề nghị đính chính không được tự duyệt.'),
        findsOneWidget,
      );
      await _tap(tester, 'cf-note-submit');
      expect(repository.keys[0], isNot(repository.keys[1]));
    });

    test('everyCommandSendsIdempotencyKey over gateway paths', () async {
      final transport = _Transport()..data = <String, Object?>{};
      final repository = GatewayRecordCollaborationRepository(
        _client(transport),
      );
      await repository.openCourse(
        patientId: _patient,
        problem: 'p',
        openingReason: 'r',
        recordId: _record,
        idempotencyKey: 'k1',
      );
      await repository.attachVisit(
        courseId: _course,
        recordId: _record,
        idempotencyKey: 'k2',
      );
      await repository.closeCourse(
        courseId: _course,
        decision: 'RESOLVED',
        note: 'n',
        idempotencyKey: 'k3',
      );
      await repository.requestHandover(
        _record,
        toDoctorId: _other,
        reason: 'r',
        pendingItems: 'p',
        idempotencyKey: 'k4',
      );
      await repository.acceptHandover(
        _record,
        _handover,
        idempotencyKey: 'k5',
      );
      await repository.addNote(_record, opinion: 'o', idempotencyKey: 'k6');
      await repository.requestAmendment(
        _record,
        section: 'ADVICE',
        amendedText: 'a',
        reason: 'r',
        idempotencyKey: 'k7',
      );
      await repository.decideAmendment(
        _record,
        _amendment,
        decision: 'REJECTED',
        note: 'n',
        idempotencyKey: 'k8',
      );
      final calls = [
        for (final r in transport.requests) '${r.method} ${r.path}',
      ];
      expect(calls, [
        'POST v2/treatment-courses',
        'POST v2/treatment-courses/$_course/visits',
        'PUT v2/treatment-courses/$_course/closure',
        'POST v2/records/$_record/handovers',
        'PUT v2/records/$_record/handovers/$_handover/acceptance',
        'POST v2/records/$_record/consultation-notes',
        'POST v2/records/$_record/amendments',
        'PUT v2/records/$_record/amendments/$_amendment/decision',
      ]);
      expect([
        for (final r in transport.requests) r.headers['Idempotency-Key'],
      ], ['k1', 'k2', 'k3', 'k4', 'k5', 'k6', 'k7', 'k8']);
      expect(transport.requests[0].data, {
        'patientId': _patient,
        'problem': 'p',
        'openingReason': 'r',
        'recordId': _record,
      });
      expect(transport.requests[2].data, {'decision': 'RESOLVED', 'note': 'n'});
      expect(transport.requests[7].data, {'decision': 'REJECTED', 'note': 'n'});
    });

    test('gateway reads parse DTOs and map 404 to unavailable', () async {
      final transport = _Transport()
        ..data = [
          {
            'courseId': _course,
            'patientId': _patient,
            'departmentId': _department,
            'problem': 'Tăng huyết áp',
            'openingReason': 'Theo dõi',
            'responsibleDoctorId': _me,
            'status': 'OPEN',
            'recordIds': [_record],
            'openedAt': '2026-11-01T01:00:00Z',
            'lastVisitDate': '2026-11-10',
            'reviewDue': true,
          },
        ];
      final repository = GatewayRecordCollaborationRepository(
        _client(transport),
      );
      final courses = await repository.courses(_patient);
      expect(courses.single.reviewDue, isTrue);
      expect(courses.single.recordIds, [_record]);
      expect(
        transport.requests.single.path,
        'v2/patients/$_patient/treatment-courses',
      );
      transport.status = 404;
      await expectLater(
        repository.courses(_patient),
        throwsA(isA<CollaborationUnavailable>()),
      );
      transport.status = 200;
      transport.data = [
        {
          'amendmentId': _amendment,
          'recordId': _record,
          'section': 'ADVICE',
          'originalText': 'a',
          'amendedText': 'b',
          'reason': 'r',
          'authorStaffId': _me,
          'status': 'APPROVED',
          'requestedAt': '2026-11-10T02:00:00Z',
          'contentHash': _hash,
        },
      ];
      final pending = await repository.pendingAmendments();
      expect(pending.single.contentHash, _hash);
      expect(transport.requests.last.path, 'v2/amendments/pending');
    });
  });
}
