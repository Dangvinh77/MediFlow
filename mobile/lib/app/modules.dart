import 'package:flutter/material.dart';

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
  });
  final String path, title, description, owner;
  final IconData icon;
  final List<UserRole> roles;
  final bool implemented;

  /// Hidden unless the build enables the V2 scheduling workspaces.
  final bool scheduling;
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
  ),
  AppModule(
    '/my-results/lab',
    'Kết quả xét nghiệm của tôi',
    'Chỉ số và kết luận xét nghiệm đã hoàn tất',
    'Vinh',
    Icons.biotech_outlined,
    [UserRole.patient],
    implemented: true,
  ),
  AppModule(
    '/organization',
    'Tổ chức',
    'Nhân sự và khoa phòng',
    'Vinh',
    Icons.groups_outlined,
    [UserRole.admin, UserRole.manager, UserRole.doctor, UserRole.nurse],
    implemented: true,
  ),
  AppModule(
    '/patients',
    'Bệnh nhân',
    'Hồ sơ định danh',
    'Vinh',
    Icons.person_outline,
    [UserRole.admin, UserRole.doctor, UserRole.nurse],
    implemented: true,
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
  ),
  AppModule(
    '/appointments',
    'Lịch hẹn',
    'Tiếp nhận ngoại trú',
    'Vinh',
    Icons.calendar_month_outlined,
    [UserRole.admin, UserRole.manager, UserRole.doctor, UserRole.nurse],
    implemented: true,
  ),
  AppModule(
    '/records',
    'Hồ sơ khám',
    'Chẩn đoán và kết luận',
    'Vinh',
    Icons.folder_shared_outlined,
    [UserRole.admin, UserRole.doctor, UserRole.nurse],
    implemented: true,
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
  ),
  AppModule(
    '/pharmacy',
    'Dược',
    'Thuốc và đơn thuốc',
    'Vinh',
    Icons.medication_outlined,
    [UserRole.admin, UserRole.doctor, UserRole.pharmacist],
    implemented: true,
  ),
  AppModule(
    '/billing',
    'Viện phí',
    'Hóa đơn và thanh toán',
    'Vinh',
    Icons.receipt_long_outlined,
    [UserRole.admin, UserRole.cashier, UserRole.manager],
    implemented: true,
  ),
  AppModule(
    '/support',
    'Hỗ trợ',
    'Báo cáo và thông báo',
    'Vinh',
    Icons.support_agent_outlined,
    [UserRole.admin, UserRole.manager, UserRole.nurse, UserRole.patient],
    implemented: true,
  ),
  AppModule(
    '/notifications',
    'Thông báo',
    'Lịch sử thông báo',
    'Vinh',
    Icons.notifications_outlined,
    [UserRole.admin, UserRole.nurse, UserRole.patient],
    implemented: true,
  ),
  AppModule(
    '/reports',
    'Báo cáo',
    'Thống kê hoạt động',
    'Vinh',
    Icons.bar_chart_outlined,
    [UserRole.admin, UserRole.manager],
    implemented: true,
  ),
];

bool isSupportChild(AppModule module) =>
    module.path == '/reports' || module.path == '/notifications';

List<AppModule> visibleMainModules(
  UserRole role, {
  bool schedulingEnabled = false,
}) => modules
    .where(
      (module) =>
          !isSupportChild(module) &&
          module.allows(role) &&
          (!module.scheduling || schedulingEnabled),
    )
    .toList();

List<AppModule> visibleSupportModules(UserRole role) => modules
    .where((module) => isSupportChild(module) && module.allows(role))
    .toList();
