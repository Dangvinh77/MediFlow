import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/organization.dart';
import 'access_boundary.dart';

/// The three fixed department categories, as the web `rooms.ts` names them.
const departmentCategoryLabels = {
  'CLINICAL': 'Khối lâm sàng',
  'PARACLINICAL': 'Khối cận lâm sàng',
  'ADMINISTRATIVE': 'Khối hành chính (phòng chức năng)',
};

const departmentCategoryHints = {
  'CLINICAL': 'Khoa khám bệnh theo chuyên khoa; có trưởng khoa, bác sĩ và điều dưỡng; có nhiều phòng khám.',
  'PARACLINICAL': 'Khoa hỗ trợ chẩn đoán và điều trị như Khoa Xét nghiệm, Khoa Dược; làm việc tại một phòng.',
  'ADMINISTRATIVE': 'Phòng chức năng như Hành chính quản trị, Tài chính kế toán; làm việc tại một phòng.',
};

/// Loads one list for a section, ignoring results that arrive after a reload.
mixin _Loader<T extends StatefulWidget, R> on State<T> {
  R? data;
  Object? error;
  bool loading = true;
  int _generation = 0;

  Future<R> fetch();

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> load() async {
    final generation = ++_generation;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await fetch();
      if (mounted && generation == _generation) setState(() => data = result);
    } catch (failure) {
      if (mounted && generation == _generation) setState(() => error = failure);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => loading = false);
      }
    }
  }

  Widget? loadState() {
    if (loading && data == null) {
      return const Center(child: MediFlowLoader());
    }
    if (error != null) {
      return SingleChildScrollView(
        child: RequestError(
          error!,
          onRetry: canRetryOrganization(error) ? load : null,
        ),
      );
    }
    return null;
  }
}

typedef _RoomData = ({
  List<Room> rooms,
  List<Department> departments,
  List<RoomType> types,
});

/// Mirrors the web `RoomDirectory`: every workplace room with its zone,
/// location, type, owning department and status, filterable by department or
/// room type. Room changes stay on the web admin screen.
class RoomSection extends StatefulWidget {
  const RoomSection({super.key, required this.repository});
  final OrganizationRepository repository;
  @override
  State<RoomSection> createState() => _RoomSectionState();
}

class _RoomSectionState extends State<RoomSection>
    with _Loader<RoomSection, _RoomData> {
  String _dimension = '', _target = '', _keyword = '';

  @override
  Future<_RoomData> fetch() async {
    final rooms = widget.repository.rooms();
    final departments = widget.repository.departments();
    final types = widget.repository.roomTypes();
    return (
      rooms: await rooms,
      departments: await departments,
      types: await types,
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = loadState();
    final data = this.data;
    final departmentNames = {
      for (final department in data?.departments ?? const <Department>[])
        department.id: department.name,
    };
    final typeNames = {
      for (final type in data?.types ?? const <RoomType>[])
        type.code: type.name,
    };
    final keyword = _keyword.toLowerCase();
    final visible = (data?.rooms ?? const <Room>[])
        .where(
          (room) =>
              (_target.isEmpty ||
                  (_dimension == 'department'
                      ? room.departmentId == _target
                      : room.roomType == _target)) &&
              (keyword.isEmpty ||
                  '${room.roomNumber} ${room.roomName} ${room.zone}'
                      .toLowerCase()
                      .contains(keyword)),
        )
        .toList(growable: false);
    final targets = _dimension == 'department'
        ? [
            for (final department in data?.departments ?? const <Department>[])
              (department.id, department.name),
          ]
        : _dimension == 'type'
        ? [
            for (final type in data?.types ?? const <RoomType>[])
              (type.code, type.name),
          ]
        : const <(String, String)>[];
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _SectionHeading(
                title: 'Phòng làm việc',
                description: 'Phòng khám phân bổ cho khoa, văn phòng khoa và nơi làm việc của các khoa cận lâm sàng và phòng chức năng. Loại phòng được quản lý tại Danh mục khoa phòng.',
                onRefresh: loading ? null : load,
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      key: const ValueKey('room-filter-by'),
                      initialValue: _dimension,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        label: LText('Lọc theo'),
                      ),
                      items: const [
                        DropdownMenuItem(
                          value: '',
                          child: LText('Tất cả phòng'),
                        ),
                        DropdownMenuItem(
                          value: 'department',
                          child: LText('Khoa / bộ phận'),
                        ),
                        DropdownMenuItem(
                          value: 'type',
                          child: LText('Loại phòng'),
                        ),
                      ],
                      onChanged: (value) => setState(() {
                        _dimension = value ?? '';
                        _target = '';
                      }),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      key: ValueKey('room-filter-target-$_dimension'),
                      initialValue: _target,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        label: LText('Đối tượng lọc'),
                      ),
                      items: [
                        const DropdownMenuItem(
                          value: '',
                          child: LText('Tất cả'),
                        ),
                        for (final (id, name) in targets)
                          DropdownMenuItem(
                            value: id,
                            child: LText(name, overflow: TextOverflow.ellipsis),
                          ),
                      ],
                      onChanged: _dimension.isEmpty
                          ? null
                          : (value) => setState(() => _target = value ?? ''),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Tìm phòng'),
                  hintText: AppL10n.text(context, 'Số phòng, tên, khu'),
                  prefixIcon: const Icon(Icons.search),
                ),
                onChanged: (value) => setState(() => _keyword = value.trim()),
              ),
            ],
          ),
        ),
        Expanded(
          child:
              state ??
              RefreshIndicator(
                onRefresh: load,
                child: visible.isEmpty
                    ? ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: const [
                          Padding(
                            padding: EdgeInsets.all(24),
                            child: LText('Chưa có phòng làm việc phù hợp.'),
                          ),
                        ],
                      )
                    : ListView.separated(
                        physics: const AlwaysScrollableScrollPhysics(),
                        padding: const EdgeInsets.all(12),
                        itemCount: visible.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 8),
                        itemBuilder: (context, index) {
                          final room = visible[index];
                          return _RoomTile(
                            room: room,
                            typeName: typeNames[room.roomType] ?? room.roomType,
                            departmentName:
                                departmentNames[room.departmentId] ?? '—',
                          );
                        },
                      ),
              ),
        ),
      ],
    );
  }
}

class _RoomTile extends StatelessWidget {
  const _RoomTile({
    required this.room,
    required this.typeName,
    required this.departmentName,
  });
  final Room room;
  final String typeName, departmentName;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodyMedium?.copyWith(
      color: context.medical.mutedText,
    );
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'P.${room.roomNumber}',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    fontFeatures: const [FontFeature.tabularFigures()],
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: LText(
                    room.roomName,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            LText('${room.zone} · ${room.location}', style: muted),
            const SizedBox(height: 2),
            LText('$typeName · $departmentName', style: muted),
            const SizedBox(height: 8),
            StatusPill(
              AppL10n.text(
                context,
                room.active ? 'Đang sử dụng' : 'Ngừng sử dụng',
              ),
              tone: room.active ? StatusTone.success : StatusTone.neutral,
            ),
          ],
        ),
      ),
    );
  }
}

/// Mirrors the web `RoomTypeCatalog`: room types grouped by department
/// category, with each type's rule and how many rooms use it.
class RoomTypeCatalogSection extends StatefulWidget {
  const RoomTypeCatalogSection({super.key, required this.repository});
  final OrganizationRepository repository;
  @override
  State<RoomTypeCatalogSection> createState() => _RoomTypeCatalogSectionState();
}

class _RoomTypeCatalogSectionState extends State<RoomTypeCatalogSection>
    with _Loader<RoomTypeCatalogSection, List<RoomType>> {
  @override
  Future<List<RoomType>> fetch() => widget.repository.roomTypes();

  @override
  Widget build(BuildContext context) {
    final state = loadState();
    final types = data ?? const <RoomType>[];
    return state ??
        RefreshIndicator(
          onRefresh: load,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(12),
            children: [
              _SectionHeading(
                title: 'Danh mục khoa phòng',
                description:
                    'Khối khoa và loại phòng được phép trong từng khối',
                onRefresh: loading ? null : load,
              ),
              for (final category in departmentTypes) ...[
                const SizedBox(height: 12),
                _CategoryCard(
                  category: category,
                  types: types
                      .where((type) => type.departmentType == category)
                      .toList(growable: false),
                ),
              ],
            ],
          ),
        );
  }
}

class _CategoryCard extends StatelessWidget {
  const _CategoryCard({required this.category, required this.types});
  final String category;
  final List<RoomType> types;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: context.medical.mutedText,
    );
    return Card(
      margin: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                LText(
                  departmentCategoryLabels[category] ?? category,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                LText(departmentCategoryHints[category] ?? '', style: muted),
              ],
            ),
          ),
          const Divider(height: 1),
          if (types.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: LText(
                'Khối này chưa có loại phòng nào; khoa/bộ phận thuộc khối sẽ không tạo được phòng làm việc.',
              ),
            )
          else
            for (final (index, type) in types.indexed) ...[
              if (index > 0) const Divider(height: 1, indent: 16),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Wrap(
                            spacing: 8,
                            runSpacing: 4,
                            crossAxisAlignment: WrapCrossAlignment.center,
                            children: [
                              LText(
                                type.name,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              if (type.schedulable)
                                StatusPill(
                                  AppL10n.text(context, 'Dùng lập buổi khám'),
                                  tone: StatusTone.info,
                                ),
                            ],
                          ),
                          if (type.description != null) ...[
                            const SizedBox(height: 2),
                            LText(type.description!, style: muted),
                          ],
                          const SizedBox(height: 4),
                          LText(type.rule, style: muted),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '${type.roomCount}',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontFeatures: const [FontFeature.tabularFigures()],
                          ),
                        ),
                        LText('Số phòng đang dùng', style: muted),
                      ],
                    ),
                  ],
                ),
              ),
            ],
        ],
      ),
    );
  }
}

class _SectionHeading extends StatelessWidget {
  const _SectionHeading({
    required this.title,
    required this.description,
    required this.onRefresh,
  });
  final String title, description;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              LText(
                title,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              LText(
                description,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: context.medical.mutedText,
                ),
              ),
            ],
          ),
        ),
        IconButton(
          tooltip: AppL10n.text(context, 'Làm mới'),
          onPressed: onRefresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
    );
  }
}
