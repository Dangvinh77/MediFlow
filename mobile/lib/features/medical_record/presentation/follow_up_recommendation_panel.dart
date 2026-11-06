import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/domain/follow_up_recommendation.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/follow_up_recommendations.dart';

final followUpRecommendationRepositoryProvider =
    Provider<FollowUpRecommendationRepository?>((ref) => null);

String _display(String isoDate) {
  final parts = isoDate.split('-');
  return parts.length == 3 ? '${parts[2]}/${parts[1]}/${parts[0]}' : isoDate;
}

String _iso(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

/// Shows a record's single, immutable follow-up advice, or lets its doctor
/// create one. A lost response resends the same key and body.
class FollowUpRecommendationPanel extends ConsumerStatefulWidget {
  const FollowUpRecommendationPanel({
    super.key,
    required this.recordId,
    required this.canCreate,
    this.patientView = false,
  });

  final String recordId;
  final bool canCreate, patientView;

  @override
  ConsumerState<FollowUpRecommendationPanel> createState() =>
      _FollowUpRecommendationPanelState();
}

class _FollowUpRecommendationPanelState
    extends ConsumerState<FollowUpRecommendationPanel> {
  final _reason = TextEditingController();
  final _preparation = TextEditingController();
  FollowUpRecommendation? _recommendation;
  Object? _loadError, _saveError;
  bool _loading = true, _saving = false;
  DateTime? _from, _through;
  FollowUpDraft? _frozen;

  FollowUpRecommendationRepository get _repository =>
      ref.read(followUpRecommendationRepositoryProvider)!;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _reason.dispose();
    _preparation.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _loadError = null;
    });
    try {
      final recommendation = await _repository.read(widget.recordId);
      if (mounted) setState(() => _recommendation = recommendation);
    } catch (error) {
      if (mounted) setState(() => _loadError = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submit() async {
    if (_saving) return;
    final draft =
        _frozen ??
        FollowUpDraft(
          recommendedFrom: _from == null ? '' : _iso(_from!),
          recommendedThrough: _through == null ? '' : _iso(_through!),
          reason: _reason.text.trim(),
          preparationInstructions: _preparation.text.trim().isEmpty
              ? null
              : _preparation.text.trim(),
        );
    final issue = draft.validate();
    if (issue != null) {
      setState(() => _saveError = ApiFailure(issue, status: 400));
      return;
    }
    setState(() {
      _saving = true;
      _saveError = null;
      _frozen = draft;
    });
    try {
      final created = await _repository.create(widget.recordId, draft);
      if (!mounted) return;
      setState(() {
        _recommendation = created;
        _frozen = null;
      });
    } catch (error) {
      if (!mounted) return;
      final unknown =
          error is! ApiFailure ||
          error.status == null ||
          error.status == 408 ||
          error.status! >= 500;
      setState(() {
        _saveError = error;
        if (!unknown) _frozen = null;
      });
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _pick({required bool from}) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: (from ? _from : _through) ?? now,
      firstDate: now.subtract(const Duration(days: 30)),
      lastDate: DateTime(now.year + 3),
    );
    if (picked == null || !mounted) return;
    setState(() {
      if (from) {
        _from = picked;
      } else {
        _through = picked;
      }
      _saveError = null;
    });
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Đề xuất tái khám',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          if (_loading)
            const Center(
              child: MediFlowLoader(semanticLabel: 'Đang tải đề xuất tái khám'),
            )
          else if (_loadError != null)
            RequestError(_loadError!, onRetry: _load)
          else if (_recommendation case final recommendation?)
            ..._saved(recommendation)
          else if (widget.canCreate)
            ..._form()
          else
            const Text('Chưa có đề xuất tái khám cho hồ sơ này.'),
        ],
      ),
    ),
  );

  List<Widget> _saved(FollowUpRecommendation value) => [
    const Text('Đề xuất đã lưu và không thể chỉnh sửa.'),
    const SizedBox(height: 8),
    Text(
      'Khoảng ngày đề xuất: ${_display(value.recommendedFrom)} – ${_display(value.recommendedThrough)}',
    ),
    ReferenceLabel(
      kind: ReferenceKind.doctor,
      id: value.preferredDoctorId,
      label: 'Bác sĩ ưu tiên',
    ),
    Text('Lý do: ${value.reason}'),
    if (value.preparationInstructions case final instructions?)
      Text('Hướng dẫn chuẩn bị: $instructions'),
    if (widget.patientView) ...[
      const SizedBox(height: 8),
      const Text('Liên hệ điều dưỡng để chọn buổi khám phù hợp.'),
    ],
  ];

  List<Widget> _form() {
    final locked = _saving || _frozen != null;
    return [
      const Text('Chưa có đề xuất tái khám.'),
      const SizedBox(height: 8),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          OutlinedButton(
            onPressed: locked ? null : () => _pick(from: true),
            child: Text(
              _from == null
                  ? 'Từ ngày khuyến nghị'
                  : 'Từ ${_display(_iso(_from!))}',
            ),
          ),
          OutlinedButton(
            onPressed: locked ? null : () => _pick(from: false),
            child: Text(
              _through == null
                  ? 'Đến ngày khuyến nghị'
                  : 'Đến ${_display(_iso(_through!))}',
            ),
          ),
        ],
      ),
      const SizedBox(height: 12),
      TextField(
        controller: _reason,
        enabled: !locked,
        maxLength: 1000,
        maxLines: 3,
        minLines: 1,
        decoration: const InputDecoration(
          labelText: 'Lý do tái khám',
          border: OutlineInputBorder(),
        ),
      ),
      const SizedBox(height: 12),
      TextField(
        controller: _preparation,
        enabled: !locked,
        maxLength: 2000,
        maxLines: 3,
        minLines: 1,
        decoration: const InputDecoration(
          labelText: 'Hướng dẫn chuẩn bị (không bắt buộc)',
          border: OutlineInputBorder(),
        ),
      ),
      if (_saveError != null) RequestError(_saveError!),
      const SizedBox(height: 8),
      FilledButton(
        onPressed: _saving ? null : _submit,
        child: Text(
          _frozen != null ? 'Gửi lại cùng đề xuất' : 'Tạo đề xuất tái khám',
        ),
      ),
    ];
  }
}
