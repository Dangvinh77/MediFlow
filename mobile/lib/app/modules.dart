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
  });
  final String path, title, description, owner;
  final IconData icon;
  final List<UserRole> roles;
  final bool implemented;
  bool allows(UserRole role) => roles.contains(role);
  bool matches(String location) =>
      location == path || location.startsWith('$path/');
}

const modules = [
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
    'Huy',
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
    'Huy',
    Icons.bar_chart_outlined,
    [UserRole.admin, UserRole.manager],
  ),
];
