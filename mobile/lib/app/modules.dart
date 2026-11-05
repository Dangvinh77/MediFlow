import 'package:flutter/material.dart';

import '../core/theme/medical_colors.dart';
import '../features/auth/domain/session.dart';

class AppModule {
  const AppModule(
    this.path,
    this.title,
    this.description,
    this.owner,
    this.icon,
    this.roles, {
    this.implemented = false,
    this.scheduling = false,
    this.headOnly = false,
    this.accent = Wayfinding.home,
    this.navLabel,
  });
  final String path, title, description, owner;
  final IconData icon;
  final List<UserRole> roles;
  final bool implemented;

  /// Hidden unless the build enables the V2 scheduling workspaces.
  final bool scheduling;

  /// Shown to a doctor only while appointed head of a department.
  final bool headOnly;

  /// This area's wayfinding colour across the home tiles, drawer and header.
  final Color accent;

  /// Short label for the bottom bar; [title] is used when it already fits.
  final String? navLabel;
  String get bottomLabel => navLabel ?? title;
  bool allows(UserRole role) => roles.contains(role);
  bool matches(String location) =>
      location == path || location.startsWith('$path/');
}

const modules = [
  AppModule(
    '/my-results/exams',
    'Kết quả khám của tôi',
    'Chẩn đoán và hướng xử trí đã hoàn tất',
    'Vinh',
    Icons.assignment_outlined,
    [UserRole.patient],
    implemented: true,
    accent: Wayfinding.record,
    navLabel: 'Khám',
  ),
  AppModule(
    '/my-results/lab',
    'Kết quả xét nghiệm của tôi',
    'Chỉ số và kết luận xét nghiệm đã hoàn tất',
    'Vinh',
    Icons.biotech_outlined,
    [UserRole.patient],
    implemented: true,
    accent: Wayfinding.lab,
    navLabel: 'Xét nghiệm',
  ),
  AppModule(
    '/organization',
    'Tổ chức',
    'Nhân sự và khoa phòng',
    'Vinh',
    Icons.groups_outlined,
    [UserRole.admin, UserRole.manager, UserRole.doctor, UserRole.nurse],
    implemented: true,
    accent: Wayfinding.organization,
  ),
  AppModule(
    '/patients',
    'Bệnh nhân',
    'Hồ sơ định danh',
    'Vinh',
    Icons.person_outline,
    [UserRole.admin, UserRole.doctor, UserRole.nurse],
    implemented: true,
    accent: Wayfinding.patient,
  ),
  AppModule(
    '/appointments/my-calendar',
    'Lịch khám của tôi',
    'Buổi khám và phiếu của bác sĩ',
    'Vinh',
    Icons.event_note_outlined,
    [UserRole.doctor],
    implemented: true,
    scheduling: true,
    accent: Wayfinding.appointment,
    navLabel: 'Lịch khám',
  ),
  AppModule(
    '/appointments/my-shifts',
    'Lịch làm việc của tôi',
    'Giờ và phòng được phân công',
    'Vinh',
    Icons.schedule_outlined,
    [UserRole.doctor],
    implemented: true,
    scheduling: true,
    accent: Wayfinding.appointment,
    navLabel: 'Lịch làm',
  ),
  AppModule(
    '/appointments/schedule',
    'Quản lý buổi khám',
    'Ca khám và phạm vi khoa',
    'Vinh',
    Icons.edit_calendar_outlined,
    [UserRole.admin, UserRole.manager],
    implemented: true,
    scheduling: true,
    accent: Wayfinding.appointment,
    navLabel: 'Buổi khám',
  ),
  AppModule(
    '/appointments/department',
    'Quản lý khoa',
    'Buổi khám và phân công bác sĩ',
    'Vinh',
    Icons.domain_outlined,
    [UserRole.doctor],
    implemented: true,
    scheduling: true,
    headOnly: true,
    accent: Wayfinding.appointment,
    navLabel: 'Khoa',
  ),
  AppModule(
    '/appointments/my-registration',
    'Phiếu khám của tôi',
    'Tra cứu phiếu đăng ký khám',
    'Vinh',
    Icons.confirmation_number_outlined,
    [UserRole.patient],
    implemented: true,
    scheduling: true,
    accent: Wayfinding.appointment,
    navLabel: 'Phiếu khám',
  ),
  AppModule(
    '/appointments',
    'Lịch hẹn',
    'Tiếp nhận ngoại trú',
    'Vinh',
    Icons.calendar_month_outlined,
    [UserRole.admin, UserRole.manager, UserRole.doctor, UserRole.nurse],
    implemented: true,
    accent: Wayfinding.appointment,
  ),
  AppModule(
    '/records',
    'Hồ sơ khám',
    'Chẩn đoán và kết luận',
    'Vinh',
    Icons.folder_shared_outlined,
    [UserRole.admin, UserRole.doctor, UserRole.nurse],
    implemented: true,
    accent: Wayfinding.record,
    navLabel: 'Hồ sơ',
  ),
  AppModule(
    '/lab',
    'Xét nghiệm',
    'Hàng đợi xét nghiệm',
    'Vinh',
    Icons.science_outlined,
    [
      UserRole.admin,
      UserRole.manager,
      UserRole.doctor,
      UserRole.nurse,
      UserRole.labTech,
    ],
    implemented: true,
    accent: Wayfinding.lab,
  ),
  AppModule(
    '/pharmacy',
    'Dược',
    'Thuốc và đơn thuốc',
    'Vinh',
    Icons.medication_outlined,
    [UserRole.admin, UserRole.doctor, UserRole.pharmacist],
    implemented: true,
    accent: Wayfinding.pharmacy,
  ),
  AppModule(
    '/billing',
    'Viện phí',
    'Hóa đơn và thanh toán',
    'Vinh',
    Icons.receipt_long_outlined,
    [UserRole.admin, UserRole.cashier, UserRole.manager],
    implemented: true,
    accent: Wayfinding.billing,
  ),
  AppModule(
    '/support',
    'Hỗ trợ',
    'Báo cáo và thông báo',
    'Vinh',
    Icons.support_agent_outlined,
    [UserRole.admin, UserRole.manager, UserRole.nurse, UserRole.patient],
    implemented: true,
    accent: Wayfinding.support,
  ),
  AppModule(
    '/notifications',
    'Thông báo',
    'Lịch sử thông báo',
    'Vinh',
    Icons.notifications_outlined,
    [UserRole.admin, UserRole.nurse, UserRole.patient],
    implemented: true,
    accent: Wayfinding.support,
  ),
  AppModule(
    '/reports',
    'Báo cáo',
    'Thống kê hoạt động',
    'Vinh',
    Icons.bar_chart_outlined,
    [UserRole.admin, UserRole.manager],
    implemented: true,
    accent: Wayfinding.support,
  ),
];

bool isSupportChild(AppModule module) =>
    module.path == '/reports' || module.path == '/notifications';

List<AppModule> visibleMainModules(
  UserRole role, {
  bool schedulingEnabled = false,
  bool headsDepartment = false,
}) => modules
    .where(
      (module) =>
          !isSupportChild(module) &&
          module.allows(role) &&
          (!module.scheduling || schedulingEnabled) &&
          (!module.headOnly || headsDepartment),
    )
    .toList();

List<AppModule> visibleSupportModules(UserRole role) => modules
    .where((module) => isSupportChild(module) && module.allows(role))
    .toList();

/// Bottom-bar order: the work a role does most comes first.
const _bottomBarPriority = [
  '/appointments/my-calendar',
  '/appointments',
  '/patients',
  '/records',
  '/lab',
  '/pharmacy',
  '/billing',
  '/my-results/exams',
  '/my-results/lab',
  '/appointments/my-registration',
  '/appointments/schedule',
  '/organization',
  '/support',
];

/// Up to three modules for the bottom bar; the drawer always lists them all.
List<AppModule> bottomBarModules(List<AppModule> visible) => [
  for (final path in _bottomBarPriority)
    ...visible.where((module) => module.path == path),
].take(3).toList();

String roleLabel(UserRole role) => switch (role) {
  UserRole.admin => 'Quản trị viên',
  UserRole.doctor => 'Bác sĩ',
  UserRole.nurse => 'Điều dưỡng',
  UserRole.pharmacist => 'Dược sĩ',
  UserRole.cashier => 'Thu ngân',
  UserRole.labTech => 'Kỹ thuật viên xét nghiệm',
  UserRole.manager => 'Quản lý',
  UserRole.patient => 'Bệnh nhân',
};
