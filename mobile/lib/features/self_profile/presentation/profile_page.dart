import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../auth/domain/session.dart';
import '../../auth/presentation/session_controller.dart';
import '../domain/self_profile.dart';
import 'own_staff_contact_form.dart';
import 'providers.dart';

class SelfProfilePage extends ConsumerWidget {
  const SelfProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(sessionProvider);
    return auth.when(
      loading: () => const Center(child: MediFlowLoader()),
      error: (error, _) => Center(
        child: Text('Không thể đọc phiên hiện tại. ${error.toString()}'),
      ),
      data: (session) => session == null
          ? const SizedBox.shrink()
          : KeyedSubtree(
              key: ValueKey(session.accessToken),
              child: _SelfProfileWorkspace(session: session),
            ),
    );
  }
}

class _SelfProfileWorkspace extends ConsumerStatefulWidget {
  const _SelfProfileWorkspace({required this.session});

  final Session session;

  @override
  ConsumerState<_SelfProfileWorkspace> createState() =>
      _SelfProfileWorkspaceState();
}

class _SelfProfileWorkspaceState extends ConsumerState<_SelfProfileWorkspace> {
  SelfProfile? _profile;
  String? _departmentName, _departmentError, _error;
  bool _loading = true, _departmentLoading = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  bool _isCurrent(int generation) =>
      mounted &&
      generation == _generation &&
      ref.read(sessionProvider).asData?.value?.accessToken ==
          widget.session.accessToken;

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _profile = null;
      _departmentName = null;
      _departmentError = null;
      _error = null;
    });
    try {
      final profile = await ref.read(readOwnProfileProvider)(widget.session);
      if (!_isCurrent(generation)) return;
      setState(() {
        _profile = profile;
        _loading = false;
      });
      if (profile is StaffSelfProfile) {
        unawaited(_loadDepartment(generation));
      }
    } catch (error) {
      if (!_isCurrent(generation)) return;
      setState(() {
        _error = _profileError(error);
        _loading = false;
      });
    }
  }

  Future<void> _loadDepartment(int generation) async {
    if (!_isCurrent(generation)) return;
    setState(() {
      _departmentLoading = true;
      _departmentError = null;
    });
    try {
      final name = await ref.read(readOwnStaffDepartmentProvider)(
        widget.session,
      );
      if (!_isCurrent(generation)) return;
      setState(() => _departmentName = name);
    } catch (error) {
      if (!_isCurrent(generation)) return;
      setState(() => _departmentError = _profileError(error));
    } finally {
      if (_isCurrent(generation)) {
        setState(() => _departmentLoading = false);
      }
    }
  }

  void _retryDepartment() => unawaited(_loadDepartment(_generation));

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: MediFlowLoader());
    }
    if (_error case final error?) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(error, key: const ValueKey('self-profile-read-error')),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              onPressed: _load,
              icon: const Icon(Icons.refresh),
              label: const Text('Tải lại hồ sơ'),
            ),
          ),
        ],
      );
    }
    final profile = _profile;
    if (profile == null) {
      return const Center(child: Text('Không tìm thấy hồ sơ cá nhân.'));
    }
    return switch (profile) {
      StaffSelfProfile() => _buildStaff(profile),
      PatientSelfProfile() => _buildPatient(profile),
    };
  }

  Widget _buildStaff(StaffSelfProfile staff) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Text(staff.fullName, style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 4),
      Text(_jobTitle(staff.jobTitle)),
      const SizedBox(height: 16),
      _ProfileCard(
        title: 'Thông tin cá nhân',
        entries: [
          _ProfileEntry('CCCD', staff.identityNumber),
          _ProfileEntry('Ngày sinh', _formatDate(staff.dateOfBirth)),
          _ProfileEntry('Giới tính', _staffGender(staff.gender)),
          _ProfileEntry('Địa chỉ', staff.address),
          _ProfileEntry('Điện thoại', staff.phoneNumber),
          _ProfileEntry('Email', staff.email),
        ],
      ),
      const SizedBox(height: 12),
      _ProfileCard(
        title: 'Thông tin công tác',
        entries: [
          _ProfileEntry(
            'Khoa/phòng công tác',
            _departmentName ??
                (_departmentLoading ? 'Đang tải…' : 'Chưa tải được đơn vị.'),
            action: _departmentError == null
                ? null
                : TextButton.icon(
                    onPressed: _retryDepartment,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Tải lại'),
                  ),
          ),
          _ProfileEntry('Chức danh', _jobTitle(staff.jobTitle)),
          _ProfileEntry('Chuyên môn', staff.specialization),
          _ProfileEntry('Trình độ chuyên môn', staff.qualification),
          _ProfileEntry('Giấy phép', staff.licenseNumber),
          _ProfileEntry('Ngày vào làm', _formatDate(staff.hireDate)),
          _ProfileEntry(
            'Trạng thái',
            staff.active ? 'Đang làm việc' : 'Đã ngừng làm việc',
          ),
        ],
      ),
      const SizedBox(height: 12),
      OwnStaffContactForm(
        key: ValueKey(staff.id),
        session: widget.session,
        profile: staff,
        onSaved: (updated) {
          if (!_isCurrent(_generation)) return;
          setState(() => _profile = updated);
        },
        onReload: () => unawaited(_load()),
      ),
    ],
  );

  Widget _buildPatient(PatientSelfProfile patient) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Text(patient.fullName, style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 4),
      const Text('Hồ sơ bệnh nhân của bạn'),
      const SizedBox(height: 16),
      _ProfileCard(
        title: 'Thông tin cá nhân',
        entries: [
          _ProfileEntry('Ngày sinh', _formatDate(patient.dateOfBirth)),
          _ProfileEntry('Giới tính', _patientGender(patient.gender)),
          _ProfileEntry('CCCD', patient.identityNumber),
          _ProfileEntry('BHYT', patient.insuranceNumber),
        ],
      ),
      const SizedBox(height: 12),
      _ProfileCard(
        title: 'Thông tin liên hệ',
        entries: [
          _ProfileEntry('Điện thoại', patient.phoneNumber),
          _ProfileEntry('Email', patient.email),
          _ProfileEntry('Địa chỉ', patient.address),
        ],
      ),
      const SizedBox(height: 12),
      const Text('Nếu cần cập nhật hồ sơ, vui lòng liên hệ bộ phận tiếp nhận.'),
    ],
  );
}

class _ProfileCard extends StatelessWidget {
  const _ProfileCard({required this.title, required this.entries});

  final String title;
  final List<_ProfileEntry> entries;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Text(title, style: Theme.of(context).textTheme.titleMedium),
          ),
          for (final entry in entries)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: Wrap(
                alignment: WrapAlignment.spaceBetween,
                runAlignment: WrapAlignment.start,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                runSpacing: 4,
                children: [
                  SizedBox(
                    width: 132,
                    child: Text(
                      entry.label,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                  ConstrainedBox(
                    constraints: BoxConstraints(
                      maxWidth: MediaQuery.sizeOf(context).width - 210,
                    ),
                    child: Text(
                      entry.value ?? '—',
                      softWrap: true,
                      textAlign: TextAlign.end,
                    ),
                  ),
                  if (entry.action != null) entry.action!,
                ],
              ),
            ),
        ],
      ),
    ),
  );
}

class _ProfileEntry {
  const _ProfileEntry(this.label, this.value, {this.action});

  final String label;
  final String? value;
  final Widget? action;
}

String _jobTitle(String value) => switch (value) {
  'DOCTOR' => 'Bác sĩ',
  'NURSE' => 'Điều dưỡng',
  'TECHNICIAN' => 'Kỹ thuật viên',
  'PHARMACIST' => 'Dược sĩ',
  'CASHIER' => 'Thu ngân',
  'ADMINISTRATIVE' => 'Hành chính',
  _ => value,
};

String _staffGender(String? value) => switch (value) {
  'MALE' => 'Nam',
  'FEMALE' => 'Nữ',
  'OTHER' => 'Khác',
  _ => '—',
};

String _patientGender(String value) => value == 'M' ? 'Nam' : 'Nữ';

String? _formatDate(String? value) {
  if (value == null || value.isEmpty) return null;
  final date = DateTime.tryParse(value);
  if (date == null) return value;
  return '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
}

String _profileError(Object error) => switch (error) {
  ApiFailure(:final message) => message,
  FormatException(:final message) => message.toString(),
  ArgumentError(:final message) => message.toString(),
  _ => 'Không thể tải hồ sơ cá nhân.',
};
