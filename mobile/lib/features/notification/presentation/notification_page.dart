import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/domain/page_result.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/notification.dart' as domain;
import 'notification_detail_page.dart';
import 'notification_display.dart';
import 'notification_request_error.dart';
import 'providers.dart';

class NotificationPage extends ConsumerWidget {
  const NotificationPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(notificationRepositoryProvider);
    if (access == null || repository == null) {
      return const Center(child: Text('Chức năng Thông báo chưa sẵn sàng.'));
    }
    if (!const {'ADMIN', 'NURSE', 'PATIENT'}.contains(access.role)) {
      return const Center(child: Text('Bạn không có quyền xem thông báo.'));
    }
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, access.role, repository)),
      child: DefaultTabController(
        length: access.role == 'ADMIN' ? 2 : 1,
        child: Column(
          children: [
            if (access.role == 'ADMIN')
              const TabBar(
                isScrollable: true,
                tabs: [
                  Tab(text: 'Lịch sử'),
                  Tab(text: 'Gửi thông báo'),
                ],
              ),
            Expanded(
              child: TabBarView(
                children: [
                  NotificationHistoryPage(
                    repository: repository,
                    role: access.role,
                    selfPatientId: access.patientId,
                  ),
                  if (access.role == 'ADMIN')
                    NotificationSendPage(repository: repository),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class NotificationHistoryPage extends StatefulWidget {
  const NotificationHistoryPage({
    super.key,
    required this.repository,
    required this.role,
    this.selfPatientId,
  });
  final domain.NotificationRepository repository;
  final String role;
  final String? selfPatientId;
  @override
  State<NotificationHistoryPage> createState() =>
      _NotificationHistoryPageState();
}

class _NotificationHistoryPageState extends State<NotificationHistoryPage>
    with AutomaticKeepAliveClientMixin<NotificationHistoryPage> {
  final _patient = TextEditingController();
  String? _submitted;
  PageResult<domain.PatientNotification>? _page;
  Object? _error;
  bool _loading = false;
  int _generation = 0, _requestedPage = 0;

  @override
  void initState() {
    super.initState();
    if (widget.role == 'PATIENT' && widget.selfPatientId != null) {
      _patient.text = widget.selfPatientId!;
      _search();
    }
  }

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _generation++;
    _patient.dispose();
    super.dispose();
  }

  void _changed(String value) {
    if (value.trim() == _submitted) return;
    _generation++;
    setState(() {
      _submitted = null;
      _page = null;
      _error = null;
      _loading = false;
    });
  }

  Future<void> _search([int page = 0]) async {
    final id = _patient.text.trim();
    if (!domain.isNotificationUuid(id)) {
      setState(() {
        _submitted = null;
        _page = null;
        _error = const FormatException('Vui lòng chọn lại bệnh nhân.');
      });
      return;
    }
    final generation = ++_generation;
    setState(() {
      _submitted = id;
      _loading = true;
      _error = null;
      _page = null;
      _requestedPage = page;
    });
    try {
      final result = await domain.ReadPatientNotifications(widget.repository)(
        id,
        page: page,
      );
      if (mounted && generation == _generation) setState(() => _page = result);
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  bool _canRetry(Object? error) =>
      error is! FormatException &&
      error is! ArgumentError &&
      (error is! ApiFailure ||
          error.status == null ||
          error.status! >= 500 ||
          error.status == 429);

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Lịch sử thông báo',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              if (widget.role != 'PATIENT')
                ReferencePicker(
                  kind: ReferenceKind.patient,
                  label: 'Bệnh nhân',
                  value: _patient.text.isEmpty ? null : _patient.text,
                  onChanged: _loading
                      ? null
                      : (id) {
                          _patient.text = id ?? '';
                          _changed(_patient.text);
                        },
                ),
              if (widget.role == 'PATIENT')
                Text(
                  widget.selfPatientId == null
                      ? 'Tài khoản chưa liên kết hồ sơ bệnh nhân. Vui lòng liên hệ tiếp nhận.'
                      : 'Thông báo dành cho bạn',
                ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  FilledButton.icon(
                    onPressed:
                        _loading ||
                            (widget.role == 'PATIENT' &&
                                widget.selfPatientId == null)
                        ? null
                        : () => _search(),
                    icon: const Icon(Icons.search),
                    label: const Text('Tra cứu'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _loading || _submitted == null
                        ? null
                        : () => _search(_requestedPage),
                    icon: const Icon(Icons.refresh),
                    label: const Text('Làm mới'),
                  ),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _error != null
              ? NotificationRequestError(
                  _error!,
                  onRetry: _canRetry(_error)
                      ? () => _search(_requestedPage)
                      : null,
                )
              : _page == null
              ? Center(
                  child: Text(
                    widget.role == 'PATIENT'
                        ? 'Chưa có lịch sử thông báo để hiển thị.'
                        : 'Chọn bệnh nhân để xem lịch sử.',
                  ),
                )
              : _history(_page!),
        ),
      ],
    );
  }

  Widget _history(PageResult<domain.PatientNotification> page) => Column(
    children: [
      Expanded(
        child: RefreshIndicator(
          onRefresh: () => _search(page.number),
          child: page.items.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    Padding(
                      padding: EdgeInsets.all(24),
                      child: Text(
                        'Chưa có thông báo.',
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ],
                )
              : ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  itemCount: page.items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final item = page.items[index];
                    return Card(
                      child: ListTile(
                        title: Text(item.title),
                        subtitle: Text(
                          '${notificationChannelLabel(item.channel)} · '
                          '${notificationStatusLabel(item.status)} · '
                          '${notificationTimeLabel(item.createdAt)}',
                        ),
                        trailing: widget.role == 'NURSE'
                            ? null
                            : const Icon(Icons.chevron_right),
                        onTap: widget.role == 'NURSE'
                            ? null
                            : () => Navigator.of(context).push(
                                MaterialPageRoute<void>(
                                  builder: (_) => Scaffold(
                                    appBar: AppBar(
                                      title: const Text('Chi tiết thông báo'),
                                    ),
                                    body: NotificationDetailPage(
                                      notificationId: item.id,
                                    ),
                                  ),
                                ),
                              ),
                      ),
                    );
                  },
                ),
        ),
      ),
      SafeArea(
        top: false,
        child: Wrap(
          alignment: WrapAlignment.center,
          children: [
            IconButton(
              tooltip: 'Trang trước',
              onPressed: page.number > 0
                  ? () => _search(page.number - 1)
                  : null,
              icon: const Icon(Icons.chevron_left),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(
                'Trang ${page.number + 1}/${page.totalPages == 0 ? 1 : page.totalPages} · ${page.totalElements} thông báo',
              ),
            ),
            IconButton(
              tooltip: 'Trang sau',
              onPressed: page.hasNext ? () => _search(page.number + 1) : null,
              icon: const Icon(Icons.chevron_right),
            ),
          ],
        ),
      ),
    ],
  );
}

class NotificationSendPage extends StatefulWidget {
  const NotificationSendPage({super.key, required this.repository});
  final domain.NotificationRepository repository;
  @override
  State<NotificationSendPage> createState() => _NotificationSendPageState();
}

class _NotificationSendPageState extends State<NotificationSendPage>
    with AutomaticKeepAliveClientMixin<NotificationSendPage> {
  final _patient = TextEditingController();
  final _title = TextEditingController();
  final _content = TextEditingController();
  final _address = TextEditingController();
  String _channel = 'IN_APP';
  domain.PatientNotification? _receipt;
  Object? _error;
  bool _busy = false, _uncertain = false, _reconciled = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _patient.dispose();
    _title.dispose();
    _content.dispose();
    _address.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    if (_busy || _uncertain || _receipt != null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await domain.SendNotification(widget.repository)(
        domain.SendNotificationCommand(
          patientId: _patient.text.trim(),
          title: _title.text,
          content: _content.text,
          channel: _channel,
          recipientAddress: _channel == 'IN_APP' ? null : _address.text,
        ),
      );
      if (mounted) setState(() => _receipt = result);
    } catch (error) {
      if (error is ArgumentError) {
        if (mounted) setState(() => _error = error);
        return;
      }
      if (!mounted) return;
      final uncertain =
          error is! ApiFailure ||
          error.status == null ||
          error.status == 408 ||
          error.status! >= 500;
      setState(() {
        _error = error;
        _uncertain = uncertain;
        _reconciled = false;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  bool get _locked => _busy || _uncertain || _receipt != null;

  void _clearForNew() {
    setState(() {
      _receipt = null;
      _error = null;
      _uncertain = false;
      _reconciled = false;
      _title.clear();
      _content.clear();
      _address.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Gửi thông báo', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        const Text('Chọn bệnh nhân nhận thông báo.'),
        const SizedBox(height: 12),
        ReferencePicker(
          kind: ReferenceKind.patient,
          label: 'Bệnh nhân',
          value: _patient.text.isEmpty ? null : _patient.text,
          onChanged: _locked
              ? null
              : (id) => setState(() => _patient.text = id ?? ''),
        ),
        TextField(
          controller: _title,
          enabled: !_locked,
          maxLength: 255,
          decoration: const InputDecoration(
            labelText: 'Tiêu đề',
            counterText: '',
          ),
        ),
        TextField(
          controller: _content,
          enabled: !_locked,
          minLines: 3,
          maxLines: 6,
          decoration: const InputDecoration(labelText: 'Nội dung'),
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          initialValue: _channel,
          decoration: const InputDecoration(labelText: 'Kênh gửi'),
          items: const [
            DropdownMenuItem(value: 'IN_APP', child: Text('Trong ứng dụng')),
            DropdownMenuItem(value: 'EMAIL', child: Text('Email')),
            DropdownMenuItem(value: 'SMS', child: Text('SMS')),
          ],
          onChanged: _locked
              ? null
              : (value) => setState(() => _channel = value!),
        ),
        if (_channel != 'IN_APP') ...[
          const SizedBox(height: 8),
          TextField(
            controller: _address,
            enabled: !_locked,
            maxLength: 150,
            keyboardType: _channel == 'EMAIL'
                ? TextInputType.emailAddress
                : TextInputType.phone,
            decoration: InputDecoration(
              labelText: _channel == 'EMAIL'
                  ? 'Email nhận'
                  : 'Số điện thoại nhận',
            ),
          ),
        ],
        if (_error != null) NotificationRequestError(_error!),
        if (_receipt != null) ...[
          Card(
            child: ListTile(
              title: Text(
                'Kết quả gửi: ${notificationStatusLabel(_receipt!.status)} · '
                '${notificationChannelLabel(_receipt!.channel)}',
              ),
              subtitle: const Text(
                'Bạn có thể xem kết quả trong lịch sử thông báo.',
              ),
            ),
          ),
          if (_receipt!.failureReason != null)
            Text(notificationFailureLabel(_receipt!.failureReason)),
          OutlinedButton(
            onPressed: _clearForNew,
            child: const Text('Soạn thông báo khác'),
          ),
        ] else if (_uncertain) ...[
          const Text(
            'Kết quả gửi chưa xác định. Hãy kiểm tra lịch sử thông báo của bệnh nhân trước khi gửi lại để tránh thông báo trùng.',
          ),
          CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            value: _reconciled,
            onChanged: (value) => setState(() => _reconciled = value ?? false),
            title: const Text(
              'Tôi đã kiểm tra lịch sử và xác nhận chưa có thông báo trùng.',
            ),
          ),
          OutlinedButton(
            onPressed: !_reconciled
                ? null
                : () => setState(() {
                    _uncertain = false;
                    _error = null;
                  }),
            child: const Text('Tiếp tục soạn sau khi kiểm tra'),
          ),
        ] else
          FilledButton(
            onPressed: _busy ? null : _send,
            child: Text(_busy ? 'Đang gửi…' : 'Gửi thông báo'),
          ),
      ],
    );
  }
}
