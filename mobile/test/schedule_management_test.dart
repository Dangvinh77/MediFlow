import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/modules.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/appointment/domain/schedule_management.dart';
import 'package:mediflow_mobile/features/appointment/presentation/schedule_management_pages.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';

const department = '10000000-0000-4000-8000-000000000091';
const doctor = '10000000-0000-4000-8000-000000000092';
const room = '10000000-0000-4000-8000-000000000093';
const account = '10000000-0000-4000-8000-000000000094';

ManagedSession session({String status = 'OPEN', int revision = 2}) =>
    ManagedSession(
      id: '10000000-0000-4000-8000-000000000095',
      revision: revision,
      doctorId: doctor,
      departmentId: department,
      roomId: room,
      date: '2026-11-05',
      startTime: '08:00',
      endTime: '12:00',
      breaks: const [SessionBreak(startTime: '10:00', endTime: '10:15')],
      capacity: 20,
      status: status,
      confirmedCount: 3,
      availableCount: 17,
    );

ManagedShift shift({String status = 'ACTIVE'}) => ManagedShift(
  id: '10000000-0000-4000-8000-000000000096',
  revision: 5,
  doctorId: doctor,
  departmentId: department,
  roomId: room,
  date: '2026-11-05',
  startTime: '08:00',
  endTime: '12:00',
  status: status,
);

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

class FakeManagement implements ScheduleManagementRepository {
  var sessionRows = <ManagedSession>[session()];
  var shiftRows = <ManagedShift>[shift()];
  var heads = <ManagedDepartment>[
    const ManagedDepartment(id: department, name: 'Khoa Nội'),
  ];
  HeadAppointment head = const HeadAppointment(
    departmentId: department,
    revision: 1,
    staffId: null,
    history: [],
  );
  final commands = <ScheduleCommand>[];
  final results = <Object?>[];
  int sessionReads = 0;

  @override
  Future<PageResult<ManagedSession>> sessions(
    String departmentId, {
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async {
    sessionReads++;
    return PageResult(
      items: sessionRows,
      number: page,
      totalPages: 1,
      totalElements: sessionRows.length,
    );
  }

  @override
  Future<PageResult<ManagedShift>> shifts(
    String departmentId, {
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async => PageResult(
    items: shiftRows,
    number: page,
    totalPages: 1,
    totalElements: shiftRows.length,
  );

  @override
  Future<List<ConsultationRoom>> rooms(String departmentId) async => const [
    ConsultationRoom(id: room, name: 'Phòng 1'),
  ];

  @override
  Future<DepartmentSummary> summary(
    String departmentId, {
    required String fromDate,
    required String toDate,
  }) async => const DepartmentSummary(
    openSessions: 2,
    stoppedSessions: 1,
    capacity: 60,
    available: 40,
    held: 3,
    paymentLocked: 2,
    confirmed: 10,
    consumed: 5,
  );

  @override
  Future<List<ManagedDepartment>> managementDepartments() async => heads;

  @override
  Future<List<ManagedDepartment>> activeDepartments() async => const [
    ManagedDepartment(id: department, name: 'Khoa Nội'),
  ];

  @override
  Future<HeadAppointment> headAppointment(String departmentId) async => head;

  @override
  Future<void> execute(ScheduleCommand command) async {
    commands.add(command);
    if (results.isEmpty) return;
    final next = results.removeAt(0);
    if (next != null) throw next;
  }
}

Widget host(
  Widget child, {
  required String role,
  required FakeManagement repository,
  bool scheduling = true,
}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(sessionIdentity: 'session', role: role),
    ),
    referenceReaderProvider.overrideWithValue(References()),
    scheduleManagementRepositoryProvider.overrideWithValue(repository),
    appConfigProvider.overrideWithValue(
      AppConfig(
        'http://localhost:8080/api',
        scheduledPaymentsEnabled: scheduling,
      ),
    ),
  ],
  child: MaterialApp(home: Scaffold(body: child)),
);

void useTall(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void main() {
  late FakeManagement repository;

  setUp(() => repository = FakeManagement());

  group('navigation', () {
    List<String> paths(
      UserRole role, {
      bool scheduling = true,
      bool heads = false,
    }) => [
      for (final module in visibleMainModules(
        role,
        schedulingEnabled: scheduling,
        headsDepartment: heads,
      ))
        module.path,
    ];

    test('management menus follow the web roles', () {
      expect(paths(UserRole.admin), contains('/appointments/schedule'));
      expect(paths(UserRole.doctor), isNot(contains('/appointments/schedule')));
      expect(paths(UserRole.nurse), isNot(contains('/appointments/schedule')));
      expect(
        paths(UserRole.admin, scheduling: false),
        isNot(contains('/appointments/schedule')),
      );
    });

    test('only an appointed head sees department management', () {
      expect(
        paths(UserRole.doctor),
        isNot(contains('/appointments/department')),
      );
      expect(
        paths(UserRole.doctor, heads: true),
        contains('/appointments/department'),
      );
      expect(
        paths(UserRole.nurse, heads: true),
        isNot(contains('/appointments/department')),
      );
    });

    test('guards resolve management paths before /appointments', () {
      for (final path in [
        '/appointments/schedule',
        '/appointments/department',
      ]) {
        expect(modules.where((item) => item.matches(path)).first.path, path);
      }
    });
  });

  group('commands', () {
    test('a session update carries its revision and reason', () {
      final command = ScheduleCommand.updateSession(
        session(),
        doctorId: doctor,
        roomId: room,
        date: '2026-11-06',
        startTime: '09:00',
        endTime: '11:00',
        breaks: const [],
        capacity: 10,
        reason: 'Đổi giờ',
      );
      expect(command.method, 'PUT');
      expect(command.path, endsWith(session().id));
      expect(command.body['expectedRevision'], 2);
      expect(command.body['reason'], 'Đổi giờ');
      expect(command.body['departmentId'], department);
    });

    test('a session from a shift keeps its doctor, room and date', () {
      final command = ScheduleCommand.sessionFromShift(
        shift(),
        startTime: '08:30',
        endTime: '11:30',
        breaks: const [],
        capacity: 12,
      );
      expect(command.path, endsWith('/consultation-sessions'));
      expect(command.body['doctorId'], doctor);
      expect(command.body['roomId'], room);
      expect(command.body['sessionDate'], '2026-11-05');
    });

    test('dismissal sends a null staff', () {
      final head = ScheduleCommand.replaceHead(
        const HeadAppointment(
          departmentId: department,
          revision: 3,
          staffId: doctor,
          history: [],
        ),
        staffId: null,
        reason: 'Hết nhiệm kỳ',
      );
      expect(head.body.containsKey('staffId'), isTrue);
      expect(head.body['staffId'], isNull);
      expect(head.body['expectedRevision'], 3);
    });

    test('time and capacity rules match the web form', () {
      expect(validateTimeRange('08:00', '12:00'), isNull);
      expect(validateTimeRange('12:00', '08:00'), isNotNull);
      expect(validateTimeRange('8:00', '12:00'), isNotNull);
      expect(validateCapacity('200'), isNull);
      expect(validateCapacity('0'), isNotNull);
      expect(validateCapacity('201'), isNotNull);
    });

    test('rejections translate; lost outcomes stay unknown', () {
      const conflict = ApiFailure('x', status: 409, code: 'SHIFT_OVERLAP');
      expect(scheduleOutcomeUnknown(conflict), isFalse);
      expect(
        (friendlyScheduleError(conflict) as ApiFailure).message,
        contains('trùng giờ'),
      );
      expect(scheduleOutcomeUnknown(const ApiFailure('x')), isTrue);
      expect(
        scheduleOutcomeUnknown(const ApiFailure('x', status: 503)),
        isTrue,
      );
    });
  });

  group('session administration', () {
    Widget panel() => host(
      const SingleChildScrollView(
        child: SessionAdministrationPanel(fixedDepartmentId: department),
      ),
      role: 'DOCTOR',
      repository: repository,
    );

    testWidgets('status changes need a reason and reload the list', (
      tester,
    ) async {
      useTall(tester);
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Ngừng nhận'));
      await tester.pumpAndSettle();
      expect(repository.commands, isEmpty);
      expect(find.textContaining('Nhập lý do'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, 'Lý do ngừng, mở lại hoặc hủy buổi'),
        'Bác sĩ nghỉ',
      );
      await tester.tap(find.text('Ngừng nhận'));
      await tester.pumpAndSettle();

      expect(repository.commands.single.body['status'], 'STOPPED');
      expect(repository.commands.single.body['expectedRevision'], 2);
      expect(repository.sessionReads, 2);
    });

    testWidgets('a stopped session reopens; a cancelled one has no actions', (
      tester,
    ) async {
      useTall(tester);
      repository.sessionRows = [session(status: 'STOPPED')];
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();
      expect(find.text('Mở lại'), findsOneWidget);

      repository.sessionRows = [session(status: 'CANCELLED')];
      await tester.tap(find.text('Tải buổi đã lập'));
      await tester.pumpAndSettle();
      expect(find.text('Sửa'), findsNothing);
      expect(find.text('Hủy buổi'), findsNothing);
    });

    testWidgets('a lost status change locks and resends the same command', (
      tester,
    ) async {
      useTall(tester);
      repository.results.add(const ApiFailure('Mất kết nối'));
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, 'Lý do ngừng, mở lại hoặc hủy buổi'),
        'Bác sĩ nghỉ',
      );
      await tester.tap(find.text('Hủy buổi'));
      await tester.pumpAndSettle();

      final buttons = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, 'Sửa'),
      );
      expect(buttons.onPressed, isNull);

      await tester.tap(find.text('Gửi lại cùng yêu cầu'));
      await tester.pumpAndSettle();

      expect(repository.commands, hasLength(2));
      expect(identical(repository.commands[0], repository.commands[1]), isTrue);
      expect(find.text('Gửi lại cùng yêu cầu'), findsNothing);
    });

    testWidgets('editing a session requires a reason', (tester) async {
      useTall(tester);
      await tester.pumpWidget(
        host(
          SessionEditorPage(
            departmentId: department,
            initialDate: DateTime(2026, 11, 5),
            session: session(),
          ),
          role: 'DOCTOR',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Lưu thay đổi buổi'));
      await tester.pumpAndSettle();
      expect(repository.commands, isEmpty);
      expect(find.text('Nhập lý do sửa buổi.'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, 'Lý do sửa buổi'),
        'Đổi phòng',
      );
      await tester.tap(find.text('Lưu thay đổi buổi'));
      await tester.pumpAndSettle();

      final body = repository.commands.single.body;
      expect(body['expectedRevision'], 2);
      expect(body['breaks'], [
        {'startTime': '10:00', 'endTime': '10:15'},
      ]);
    });

    testWidgets('a new session needs a doctor and room', (tester) async {
      useTall(tester);
      await tester.pumpWidget(
        host(
          SessionEditorPage(
            departmentId: department,
            initialDate: DateTime(2026, 11, 5),
          ),
          role: 'ADMIN',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Tạo buổi khám'));
      await tester.pumpAndSettle();

      expect(repository.commands, isEmpty);
      expect(find.text('Chọn bác sĩ và phòng trước khi lưu.'), findsOneWidget);
    });
  });

  group('management pages', () {
    testWidgets('admins plan sessions across departments', (tester) async {
      useTall(tester);
      await tester.pumpWidget(
        host(
          const ScheduleManagementPage(),
          role: 'ADMIN',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Lập và quản lý buổi khám'), findsOneWidget);
    });

    testWidgets('other roles cannot open schedule management', (tester) async {
      await tester.pumpWidget(
        host(
          const ScheduleManagementPage(),
          role: 'NURSE',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Bạn không có quyền quản lý buổi khám.'),
        findsOneWidget,
      );
    });

    testWidgets('a doctor without an appointment is refused', (tester) async {
      repository.heads = const [];
      await tester.pumpWidget(
        host(
          const DepartmentManagementPage(),
          role: 'DOCTOR',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Tài khoản không có bổ nhiệm trưởng khoa đang hiệu lực.'),
        findsOneWidget,
      );
    });

    testWidgets('a department head sees activity and every workspace', (
      tester,
    ) async {
      useTall(tester);
      await tester.pumpWidget(
        host(
          const DepartmentManagementPage(),
          role: 'DOCTOR',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Hoạt động khoa'), findsOneWidget);
      expect(find.text('60'), findsOneWidget);
      await tester.tap(find.text('Lịch làm việc bác sĩ').first);
      await tester.pumpAndSettle();
      expect(find.text('Tạo buổi khám từ giờ làm việc'), findsOneWidget);
    });
  });

  group('shifts', () {
    Widget panel() => host(
      const SingleChildScrollView(
        child: DepartmentShiftsPanel(departmentId: department),
      ),
      role: 'DOCTOR',
      repository: repository,
    );

    testWidgets('cancelling needs a reason and shows a blocked shift', (
      tester,
    ) async {
      useTall(tester);
      repository.results.add(
        const ApiFailure('x', status: 409, code: 'SHIFT_HAS_SESSIONS'),
      );
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Hủy giờ làm việc'));
      await tester.pumpAndSettle();
      expect(repository.commands, isEmpty);

      await tester.enterText(
        find.widgetWithText(TextField, 'Lý do hủy giờ làm việc'),
        'Đổi lịch',
      );
      await tester.tap(find.text('Hủy giờ làm việc'));
      await tester.pumpAndSettle();

      expect(repository.commands.single.body['expectedRevision'], 5);
      expect(find.textContaining('chưa thể sửa hoặc hủy'), findsOneWidget);
      expect(find.text('Gửi lại cùng yêu cầu'), findsNothing);
    });

    testWidgets('a cancelled shift has no actions', (tester) async {
      useTall(tester);
      repository.shiftRows = [shift(status: 'CANCELLED')];
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();

      expect(find.text('Đã hủy'), findsOneWidget);
      expect(find.text('Sửa giờ làm việc'), findsNothing);
    });

    testWidgets('a session stays inside its shift', (tester) async {
      useTall(tester);
      await tester.pumpWidget(
        host(
          ShiftSessionPage(shift: shift()),
          role: 'DOCTOR',
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, 'Giờ kết thúc'),
        '13:00',
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Tạo buổi khám'));
      await tester.pumpAndSettle();

      expect(repository.commands, isEmpty);
      expect(
        find.textContaining('nằm trong giờ làm việc đã phân công'),
        findsOneWidget,
      );
    });
  });

  group('head appointment', () {
    Widget panel() => host(
      const SingleChildScrollView(
        child: HeadAppointmentPanel(departmentId: department),
      ),
      role: 'ADMIN',
      repository: repository,
    );

    testWidgets('dismissal needs a reason and sends no staff', (tester) async {
      useTall(tester);
      repository.head = const HeadAppointment(
        departmentId: department,
        revision: 4,
        staffId: doctor,
        history: [
          HeadAppointmentTerm(
            staffId: doctor,
            startedAt: '2026-11-01T01:00:00Z',
          ),
        ],
      );
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();

      expect(find.text('Trưởng khoa đương nhiệm: Tên doctor'), findsOneWidget);
      expect(find.text('Lịch sử bổ nhiệm (1)'), findsOneWidget);
      expect(
        tester
            .widget<OutlinedButton>(
              find.widgetWithText(OutlinedButton, 'Miễn nhiệm trưởng khoa'),
            )
            .onPressed,
        isNull,
      );

      await tester.enterText(
        find.widgetWithText(TextField, 'Lý do'),
        'Hết nhiệm kỳ',
      );
      await tester.pump();
      await tester.tap(find.text('Miễn nhiệm trưởng khoa'));
      await tester.pumpAndSettle();

      expect(repository.commands.single.body['staffId'], isNull);
      expect(repository.commands.single.body['expectedRevision'], 4);
      expect(find.text('Đã miễn nhiệm trưởng khoa.'), findsOneWidget);
    });

    testWidgets('appointment needs a chosen doctor', (tester) async {
      await tester.pumpWidget(panel());
      await tester.pumpAndSettle();

      expect(find.text('Chưa có trưởng khoa đương nhiệm.'), findsOneWidget);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Xác nhận bổ nhiệm'),
            )
            .onPressed,
        isNull,
      );
    });
  });

  testWidgets('the session editor fits a narrow screen with large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(sessionIdentity: 's', role: 'ADMIN'),
          ),
          referenceReaderProvider.overrideWithValue(References()),
          scheduleManagementRepositoryProvider.overrideWithValue(repository),
        ],
        child: MaterialApp(
          builder: (context, page) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: const TextScaler.linear(1.45)),
            child: page!,
          ),
          home: SessionEditorPage(
            departmentId: department,
            initialDate: DateTime(2026, 11, 5),
            session: session(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
