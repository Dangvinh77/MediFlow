import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/modules.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/follow_up_recommendation.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/appointment/domain/outpatient_schedule.dart';
import 'package:mediflow_mobile/features/appointment/presentation/outpatient_schedule_pages.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/medical_record/domain/follow_up_recommendations.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/follow_up_recommendation_panel.dart';

const sessionId = '10000000-0000-4000-8000-000000000071';
const registrationId = '10000000-0000-4000-8000-000000000072';
const patientId = '10000000-0000-4000-8000-000000000073';
const recordId = '10000000-0000-4000-8000-000000000074';

class References implements ReferenceReader {
  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async =>
      ReferenceOption(id: id, name: 'Tên ${kind.name}');

  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) async => const ReferencePage([]);
}

OutpatientRegistration sampleRegistration({
  String status = 'CONFIRMED',
  String? checkedInAt,
  FollowUpRecommendation? followUp,
}) => OutpatientRegistration(
  id: registrationId,
  revision: 3,
  doctorId: '10000000-0000-4000-8000-000000000075',
  departmentId: '10000000-0000-4000-8000-000000000076',
  roomId: '10000000-0000-4000-8000-000000000077',
  sessionDate: '2026-11-05',
  startTime: '07:00',
  endTime: '17:00',
  receptionMode: 'PREBOOKED',
  visitType: 'NEW_VISIT',
  status: status,
  reservationExpiresAt: '2026-11-05T03:00:00Z',
  checkedInAt: checkedInAt,
  queueNumber: 1,
  followUp: followUp,
);

class FakeSchedule implements OutpatientScheduleRepository {
  final calendars = <(String?, String?)>[];
  final shiftRanges = <(String, String)>[];
  final arrivals = <(int, String, String)>[];
  final arrivalResults = <Object>[];
  var current = sampleRegistration();
  var rows = <RegistrationRow>[
    const RegistrationRow(
      id: registrationId,
      patientId: patientId,
      visitType: 'NEW_VISIT',
      status: 'CONFIRMED',
      readyForExam: false,
    ),
  ];
  var mine = <OutpatientRegistration>[];

  @override
  Future<DoctorCalendar> myCalendar({String? date, String? weekStart}) async {
    calendars.add((date, weekStart));
    return DoctorCalendar(
      fromDate: '2026-11-05',
      toDate: '2026-11-05',
      sessions: const [
        ScheduleSession(
          id: sessionId,
          departmentId: '10000000-0000-4000-8000-000000000076',
          roomId: '10000000-0000-4000-8000-000000000077',
          date: '2026-11-05',
          startTime: '07:00',
          endTime: '17:00',
          status: 'OPEN',
          confirmedCount: 2,
          availableCount: 38,
        ),
      ],
    );
  }

  @override
  Future<List<DoctorWorkShift>> myWorkShifts(String from, String to) async {
    shiftRanges.add((from, to));
    return const [
      DoctorWorkShift(
        id: '10000000-0000-4000-8000-000000000078',
        departmentId: '10000000-0000-4000-8000-000000000076',
        roomId: '10000000-0000-4000-8000-000000000077',
        date: '2026-11-05',
        startTime: '08:00',
        endTime: '12:00',
        status: 'ACTIVE',
      ),
    ];
  }

  @override
  Future<PageResult<RegistrationRow>> sessionRegistrations(
    String sessionId, {
    int page = 0,
  }) async => PageResult(
    items: rows,
    number: page,
    totalPages: 1,
    totalElements: rows.length,
  );

  @override
  Future<OutpatientRegistration> registration(String registrationId) async =>
      current;

  @override
  Future<OutpatientRegistration> markArrival(
    String registrationId, {
    required int expectedRevision,
    required String idempotencyKey,
    required String correlationId,
  }) async {
    arrivals.add((expectedRevision, idempotencyKey, correlationId));
    final next = arrivalResults.removeAt(0);
    if (next is OutpatientRegistration) return next;
    throw next;
  }

  @override
  Future<PageResult<OutpatientRegistration>> myRegistrations({
    int page = 0,
  }) async => PageResult(
    items: mine,
    number: page,
    totalPages: 1,
    totalElements: mine.length,
  );
}

class FakeFollowUp implements FollowUpRecommendationRepository {
  FollowUpRecommendation? saved;
  final createResults = <Object>[];
  final drafts = <FollowUpDraft>[];

  @override
  Future<FollowUpRecommendation?> read(String recordId) async => saved;

  @override
  Future<FollowUpRecommendation> create(
    String recordId,
    FollowUpDraft draft,
  ) async {
    drafts.add(draft);
    final next = createResults.removeAt(0);
    if (next is FollowUpRecommendation) return next;
    throw next;
  }
}

const advice = FollowUpRecommendation(
  id: '10000000-0000-4000-8000-000000000079',
  sourceRegistrationId: registrationId,
  preferredDoctorId: '10000000-0000-4000-8000-000000000075',
  recommendedFrom: '2026-11-10',
  recommendedThrough: '2026-11-20',
  reason: 'Theo dõi huyết áp',
  createdAt: '2026-11-05T03:00:00Z',
  preparationInstructions: 'Nhịn ăn',
);

Widget host(
  Widget child, {
  required String role,
  required FakeSchedule schedule,
  FakeFollowUp? followUp,
  bool scheduling = true,
  double textScale = 1,
}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(sessionIdentity: 'session', role: role),
    ),
    referenceReaderProvider.overrideWithValue(References()),
    outpatientScheduleRepositoryProvider.overrideWithValue(schedule),
    followUpRecommendationRepositoryProvider.overrideWithValue(followUp),
    appConfigProvider.overrideWithValue(
      AppConfig(
        'http://localhost:8080/api',
        scheduledPaymentsEnabled: scheduling,
      ),
    ),
  ],
  child: MaterialApp(
    builder: (context, page) => MediaQuery(
      data: MediaQuery.of(context)
          .copyWith(textScaler: TextScaler.linear(textScale)),
      child: page!,
    ),
    home: Scaffold(body: child),
  ),
);

void main() {
  late FakeSchedule schedule;

  setUp(() => schedule = FakeSchedule());

  group('navigation', () {
    test('role workspaces need the scheduling build', () {
      List<String> paths(UserRole role, bool enabled) => [
        for (final module in visibleMainModules(
          role,
          schedulingEnabled: enabled,
        ))
          module.path,
      ];

      expect(paths(UserRole.doctor, true), [
        ...['/organization', '/patients'],
        '/appointments/my-calendar',
        '/appointments/my-shifts',
        ...['/appointments', '/records', '/lab', '/pharmacy'],
      ]);
      expect(
        paths(UserRole.patient, true),
        contains('/appointments/my-registration'),
      );
      expect(
        paths(UserRole.nurse, true),
        isNot(contains('/appointments/my-calendar')),
      );
      expect(
        paths(UserRole.doctor, false),
        isNot(contains('/appointments/my-calendar')),
      );
      expect(
        paths(UserRole.patient, false),
        isNot(contains('/appointments/my-registration')),
      );
    });

    test('a patient route is not caught by the staff appointments guard', () {
      final module = modules
          .where((item) => item.matches('/appointments/my-registration'))
          .first;
      expect(module.allows(UserRole.patient), isTrue);
      expect(module.allows(UserRole.doctor), isFalse);
    });
  });

  test('week ranges start on Monday in clinic time', () {
    expect(isoDate(mondayOfWeek(DateTime(2026, 11, 5))), '2026-11-02');
    expect(isoDate(mondayOfWeek(DateTime(2026, 11, 8))), '2026-11-02');
    expect(isoDate(clinicToday(DateTime.utc(2026, 11, 4, 18))), '2026-11-05');
  });

  group('doctor', () {
    testWidgets('the calendar opens a session and then a registration', (
      tester,
    ) async {
      final opened = <String>[];
      await tester.pumpWidget(
        host(
          DoctorCalendarPage(onOpenRegistration: (_, id) => opened.add(id)),
          role: 'DOCTOR',
          schedule: schedule,
        ),
      );
      await tester.pumpAndSettle();

      expect(schedule.calendars.single.$1, isNotNull);
      expect(find.text('2 đã xác nhận · 38 còn chỗ'), findsOneWidget);
      await tester.tap(find.text('Xem phiếu của buổi'));
      await tester.pumpAndSettle();

      expect(find.text('Bệnh nhân: Tên patient'), findsOneWidget);
      expect(find.text('Chưa đến · Chưa có số'), findsOneWidget);
      expect(find.text('Ghi nhận đã đến'), findsNothing);
      await tester.tap(find.text('Xem phiếu'));
      await tester.pumpAndSettle();
      expect(opened, [registrationId]);
    });

    testWidgets('the week view asks for the Monday of the week', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(const DoctorCalendarPage(), role: 'DOCTOR', schedule: schedule),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Theo tuần'));
      await tester.pumpAndSettle();

      expect(schedule.calendars.last.$1, isNull);
      expect(
        DateTime.parse(schedule.calendars.last.$2!).weekday,
        DateTime.monday,
      );
    });

    testWidgets('work shifts are read-only and span a full week', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(const DoctorWorkShiftsPage(), role: 'DOCTOR', schedule: schedule),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('08:00–12:00'), findsOneWidget);
      expect(find.byType(FilledButton), findsNothing);

      await tester.tap(find.text('Theo tuần'));
      await tester.pumpAndSettle();
      final (from, to) = schedule.shiftRanges.last;
      expect(DateTime.parse(to).difference(DateTime.parse(from)).inDays, 6);
    });

    testWidgets('the calendar stays closed without the scheduling build', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(
          const DoctorCalendarPage(),
          role: 'DOCTOR',
          schedule: schedule,
          scheduling: false,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Lịch khám chưa được mở.'), findsOneWidget);
      expect(schedule.calendars, isEmpty);
    });
  });

  group('nurse arrival', () {
    Widget page() => host(
      const SessionRegistrationsPage(
        sessionId: sessionId,
        title: '05/11',
        canMarkArrival: true,
      ),
      role: 'NURSE',
      schedule: schedule,
    );

    testWidgets('records arrival against the current revision', (tester) async {
      schedule.arrivalResults.add(sampleRegistration(checkedInAt: 'now'));
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ghi nhận đã đến'));
      await tester.pumpAndSettle();

      expect(schedule.arrivals.single.$1, 3);
    });

    testWidgets('a lost response resends the same key and revision', (
      tester,
    ) async {
      schedule.arrivalResults.addAll([
        const ApiFailure('Mất kết nối'),
        sampleRegistration(checkedInAt: 'now'),
      ]);
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ghi nhận đã đến'));
      await tester.pumpAndSettle();

      schedule.current = sampleRegistration(status: 'EXPIRED');
      await tester.tap(find.text('Gửi lại ghi nhận đã đến'));
      await tester.pumpAndSettle();

      expect(schedule.arrivals, hasLength(2));
      expect(schedule.arrivals.last, schedule.arrivals.first);
    });

    testWidgets('an arrival recorded elsewhere sends nothing', (tester) async {
      schedule.current = sampleRegistration(checkedInAt: 'now');
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ghi nhận đã đến'));
      await tester.pumpAndSettle();

      expect(schedule.arrivals, isEmpty);
    });

    testWidgets('a registration that changed state is refused', (tester) async {
      schedule.current = sampleRegistration(status: 'EXPIRED');
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ghi nhận đã đến'));
      await tester.pumpAndSettle();

      expect(schedule.arrivals, isEmpty);
      expect(find.textContaining('không còn ở trạng thái'), findsOneWidget);
      expect(find.text('Gửi lại ghi nhận đã đến'), findsNothing);
    });

    testWidgets('an arrived patient has no arrival action', (tester) async {
      schedule.rows = const [
        RegistrationRow(
          id: registrationId,
          patientId: patientId,
          visitType: 'FOLLOW_UP',
          status: 'CONFIRMED',
          readyForExam: true,
          checkedInAt: 'now',
          queueNumber: 4,
        ),
      ];
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();

      expect(find.text('Đã đến · Số 4'), findsOneWidget);
      expect(find.textContaining('Tái khám'), findsOneWidget);
      expect(find.text('Ghi nhận đã đến'), findsNothing);
    });
  });

  group('patient registrations', () {
    Widget page() => host(
      const PatientRegistrationsPage(),
      role: 'PATIENT',
      schedule: schedule,
    );

    testWidgets('explains a confirmed visit and shows follow-up advice', (
      tester,
    ) async {
      schedule.mine = [sampleRegistration(followUp: advice)];
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();

      expect(find.textContaining('05/11/2026 · 07:00–17:00'), findsOneWidget);
      expect(find.text('Đã xác nhận'), findsOneWidget);
      expect(find.text('Bác sĩ: Tên doctor'), findsOneWidget);
      expect(find.textContaining('đến quầy tiếp nhận'), findsOneWidget);
      expect(find.textContaining('10/11/2026 – 20/11/2026'), findsOneWidget);
      expect(find.text('Hướng dẫn chuẩn bị: Nhịn ăn'), findsOneWidget);
    });

    testWidgets('marks an unpaid registration as unconfirmed', (tester) async {
      schedule.mine = [sampleRegistration(status: 'AWAITING_PAYMENT')];
      await tester.pumpWidget(page());
      await tester.pumpAndSettle();

      expect(find.textContaining('Hạn thanh toán giữ chỗ'), findsOneWidget);
      expect(find.textContaining('chưa được xác nhận'), findsOneWidget);
    });

    testWidgets('staff cannot open the patient view', (tester) async {
      await tester.pumpWidget(
        host(
          const PatientRegistrationsPage(),
          role: 'DOCTOR',
          schedule: schedule,
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Chức năng dành cho bệnh nhân đăng nhập.'),
        findsOneWidget,
      );
    });
  });

  group('narrow screens with large text', () {
    Future<void> pumpNarrow(WidgetTester tester, Widget child) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(child);
      await tester.pumpAndSettle();
    }

    testWidgets('the doctor calendar fits', (tester) async {
      await pumpNarrow(
        tester,
        host(const DoctorCalendarPage(), role: 'DOCTOR', schedule: schedule),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('the arrival list fits', (tester) async {
      await pumpNarrow(
        tester,
        host(
          const SessionRegistrationsPage(
            sessionId: sessionId,
            title: '05/11/2026 · 07:00–17:00',
            canMarkArrival: true,
          ),
          role: 'NURSE',
          schedule: schedule,
          textScale: 1.45,
        ),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('the patient registration fits', (tester) async {
      schedule.mine = [sampleRegistration(followUp: advice)];
      await pumpNarrow(
        tester,
        host(
          const PatientRegistrationsPage(),
          role: 'PATIENT',
          schedule: schedule,
          textScale: 1.45,
        ),
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('follow-up recommendation', () {
    Widget panel(FakeFollowUp followUp, {bool canCreate = true}) => host(
      SingleChildScrollView(
        child: FollowUpRecommendationPanel(
          recordId: recordId,
          canCreate: canCreate,
        ),
      ),
      role: 'DOCTOR',
      schedule: schedule,
      followUp: followUp,
    );

    testWidgets('a saved recommendation is read-only', (tester) async {
      final followUp = FakeFollowUp()..saved = advice;
      await tester.pumpWidget(panel(followUp));
      await tester.pumpAndSettle();

      expect(
        find.text('Đề xuất đã lưu và không thể chỉnh sửa.'),
        findsOneWidget,
      );
      expect(find.text('Lý do: Theo dõi huyết áp'), findsOneWidget);
      expect(find.byType(TextField), findsNothing);
    });

    testWidgets('non-doctors never get the form', (tester) async {
      await tester.pumpWidget(panel(FakeFollowUp(), canCreate: false));
      await tester.pumpAndSettle();

      expect(
        find.text('Chưa có đề xuất tái khám cho hồ sơ này.'),
        findsOneWidget,
      );
      expect(find.byType(TextField), findsNothing);
    });

    test('the draft enforces the web rules', () {
      FollowUpDraft draft({
        String from = '2026-11-10',
        String through = '2026-11-20',
        String reason = 'Theo dõi',
      }) => FollowUpDraft(
        recommendedFrom: from,
        recommendedThrough: through,
        reason: reason,
      );

      expect(draft().validate(), isNull);
      expect(draft(from: '').validate(), contains('Chọn ngày'));
      expect(
        draft(through: '2026-11-01').validate(),
        contains('không được trước'),
      );
      expect(draft(reason: '').validate(), contains('Nhập lý do'));
      expect(draft(reason: 'x' * 1001).validate(), contains('1000'));
    });

    testWidgets('an incomplete form sends nothing', (tester) async {
      final followUp = FakeFollowUp();
      await tester.pumpWidget(panel(followUp));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tạo đề xuất tái khám'));
      await tester.pumpAndSettle();

      expect(followUp.drafts, isEmpty);
      expect(find.textContaining('Chọn ngày bắt đầu'), findsOneWidget);
    });
  });
}
