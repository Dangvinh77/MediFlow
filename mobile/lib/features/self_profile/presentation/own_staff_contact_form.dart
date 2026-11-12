import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../auth/domain/session.dart';
import '../../auth/presentation/session_controller.dart';
import '../domain/self_profile.dart';
import 'providers.dart';

class OwnStaffContactForm extends ConsumerStatefulWidget {
  const OwnStaffContactForm({
    super.key,
    required this.session,
    required this.profile,
    required this.onSaved,
    required this.onReload,
  });

  final Session session;
  final StaffSelfProfile profile;
  final ValueChanged<StaffSelfProfile> onSaved;
  final VoidCallback onReload;

  @override
  ConsumerState<OwnStaffContactForm> createState() =>
      _OwnStaffContactFormState();
}

class _OwnStaffContactFormState extends ConsumerState<OwnStaffContactForm> {
  late final TextEditingController _phone = TextEditingController(
    text: widget.profile.phoneNumber ?? '',
  );
  late final TextEditingController _address = TextEditingController(
    text: widget.profile.address ?? '',
  );
  bool _busy = false, _saved = false, _outcomeUnknown = false;
  String? _error;

  @override
  void didUpdateWidget(covariant OwnStaffContactForm oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.profile.phoneNumber != widget.profile.phoneNumber) {
      _phone.text = widget.profile.phoneNumber ?? '';
    }
    if (oldWidget.profile.address != widget.profile.address) {
      _address.text = widget.profile.address ?? '';
    }
    if (oldWidget.profile.phoneNumber != widget.profile.phoneNumber ||
        oldWidget.profile.address != widget.profile.address) {
      _error = null;
      _outcomeUnknown = false;
    }
  }

  @override
  void dispose() {
    _phone.dispose();
    _address.dispose();
    super.dispose();
  }

  bool get _sessionIsCurrent =>
      ref.read(sessionProvider).asData?.value?.accessToken ==
      widget.session.accessToken;

  Future<void> _save() async {
    if (_busy || !widget.profile.active) return;
    setState(() {
      _busy = true;
      _saved = false;
      _error = null;
      _outcomeUnknown = false;
    });
    try {
      final updated = await ref.read(updateOwnStaffContactProvider)(
        widget.session,
        StaffContactDraft(phoneNumber: _phone.text, address: _address.text),
      );
      if (!mounted || !_sessionIsCurrent) return;
      widget.onSaved(updated);
      setState(() => _saved = true);
    } catch (error) {
      if (!mounted || !_sessionIsCurrent) return;
      setState(() {
        _outcomeUnknown = _mayHaveUnknownOutcome(error);
        _error = _outcomeUnknown
            ? 'Chưa xác nhận được kết quả. Hãy tải lại hồ sơ để kiểm tra trạng thái.'
            : error is ArgumentError
            ? error.message.toString()
            : error is ApiFailure
            ? error.message
            : 'Không thể lưu thông tin liên hệ.';
      });
    } finally {
      if (mounted && _sessionIsCurrent) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LText(
            'Cập nhật liên hệ của bạn',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          const LText(
            'Bạn có thể sửa điện thoại và địa chỉ. Email đăng nhập chỉ do quản trị viên cập nhật.',
          ),
          const SizedBox(height: 16),
          TextFormField(
            key: const ValueKey('self-profile-phone'),
            controller: _phone,
            enabled: !_busy && widget.profile.active,
            keyboardType: TextInputType.phone,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            maxLength: 15,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Điện thoại'),
              helperText: AppL10n.text(context, 'Nhập từ 10 đến 15 chữ số.'),
            ),
            onChanged: (_) => setState(() {
              _saved = false;
              _error = null;
              _outcomeUnknown = false;
            }),
          ),
          const SizedBox(height: 12),
          TextFormField(
            key: const ValueKey('self-profile-email'),
            initialValue: widget.profile.email ?? '',
            readOnly: true,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Email'),
              helperText: AppL10n.text(
                context,
                'Liên hệ quản trị viên nếu cần đổi email đăng nhập.',
              ),
            ),
          ),
          const SizedBox(height: 12),
          TextFormField(
            key: const ValueKey('self-profile-address'),
            controller: _address,
            enabled: !_busy && widget.profile.active,
            minLines: 2,
            maxLines: 4,
            maxLength: 255,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Địa chỉ'),
            ),
            onChanged: (_) => setState(() {
              _saved = false;
              _error = null;
              _outcomeUnknown = false;
            }),
          ),
          if (!widget.profile.active)
            const LText(
              'Hồ sơ đã ngừng hoạt động nên không thể cập nhật liên hệ.',
            ),
          if (_error case final error?) ...[
            const SizedBox(height: 8),
            LText(error, key: const ValueKey('self-profile-save-error')),
            if (_outcomeUnknown)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _busy ? null : widget.onReload,
                  icon: const Icon(Icons.refresh),
                  label: const LText('Tải lại hồ sơ'),
                ),
              ),
          ],
          if (_saved)
            const Padding(
              padding: EdgeInsets.only(top: 8),
              child: LText(
                'Đã cập nhật thông tin liên hệ.',
                key: ValueKey('self-profile-save-success'),
              ),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _busy || !widget.profile.active ? null : _save,
            icon: _busy
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: LText(_busy ? 'Đang lưu…' : 'Lưu thông tin liên hệ'),
          ),
        ],
      ),
    ),
  );
}

bool _mayHaveUnknownOutcome(Object error) =>
    error is! ApiFailure || error.status == null || error.status! >= 500;
