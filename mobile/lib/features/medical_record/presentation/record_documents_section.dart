import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../domain/patient_timeline.dart';
import 'medical_record_formatters.dart';
import 'patient_timeline_section.dart';

class RecordDocumentsSection extends ConsumerStatefulWidget {
  const RecordDocumentsSection({
    super.key,
    required this.recordId,
    this.onOpenSource,
  });
  final String recordId;
  final ValueChanged<RecordDocument>? onOpenSource;

  @override
  ConsumerState<RecordDocumentsSection> createState() =>
      _RecordDocumentsSectionState();
}

class _RecordDocumentsSectionState extends ConsumerState<RecordDocumentsSection> {
  List<RecordDocument>? _documents;
  Object? _error;
  bool _loading = true;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  @override
  void didUpdateWidget(covariant RecordDocumentsSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.recordId != widget.recordId) {
      _documents = null;
      _error = null;
      _loading = true;
      Future.microtask(_load);
    }
  }

  Future<void> _load() async {
    final repository = ref.read(patientTimelineRepositoryProvider);
    final generation = ++_generation;
    final recordId = widget.recordId;
    setState(() {
      _documents = null;
      _error = null;
      _loading = true;
    });
    try {
      if (repository == null) throw StateError('Không có nguồn tài liệu hồ sơ.');
      final documents = await repository.documents(recordId);
      if (mounted && generation == _generation && recordId == widget.recordId) {
        setState(() {
          _documents = documents;
          _loading = false;
        });
      }
    } catch (error) {
      if (mounted && generation == _generation && recordId == widget.recordId) {
        setState(() {
          _error = error;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Tài liệu của lượt khám',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          if (_loading)
            const Center(child: MediFlowLoader())
          else if (_error != null)
            _failure()
          else if (_documents case final documents?)
            if (documents.isEmpty)
              const Text('Chưa có tài liệu của lượt khám.')
            else
              for (final document in documents)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    document.type == 'LAB'
                        ? Icons.science_outlined
                        : Icons.medication_outlined,
                  ),
                  title: Text(document.summary),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(document.typeLabel),
                      Text(formatClinicalInstant(document.attachedAt)),
                      if (document.afterConclusion)
                        const Text('Bổ sung sau khi kết thúc'),
                    ],
                  ),
                  trailing: widget.onOpenSource == null
                      ? null
                      : const Icon(Icons.open_in_new),
                  onTap: widget.onOpenSource == null
                      ? null
                      : () => widget.onOpenSource!(document),
                ),
        ],
      ),
    ),
  );

  Widget _failure() {
    if (_error is ApiFailure && (_error as ApiFailure).status == 403) {
      return const Text('Bạn không có quyền xem tài liệu của hồ sơ này.');
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Không tải được tài liệu của lượt khám.'),
        TextButton.icon(
          key: const Key('record-documents-retry'),
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Thử lại'),
        ),
      ],
    );
  }
}
