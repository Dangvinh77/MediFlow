import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../domain/pharmacy_identity.dart';
import '../domain/pharmacy_repository.dart';
import 'providers.dart';

class PharmacyIdentityLabel extends ConsumerWidget {
  const PharmacyIdentityLabel({
    super.key,
    required this.kind,
    required this.id,
    required this.label,
    this.showId = false,
  });
  final PharmacyIdentityKind kind;
  final String id, label;
  final bool showId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(pharmacyAccessProvider);
    // Do not query forbidden human APIs for PHARMACIST or an unsigned session.
    if (access == null || !access.canRead || !isPharmacyId(id)) {
      return const SizedBox.shrink();
    }
    final value = access.canPrescribe
        ? ref.watch(pharmacyIdentityNameProvider((kind, id)))
        : null;
    return Semantics(
      liveRegion: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '$label: ${value == null ? 'Không hiển thị với tài khoản này' : value.when(data: (name) => name, loading: () => 'Đang tải…', error: (error, _) => error is ApiFailure && error.status == 403
                      ? 'Không hiển thị với tài khoản này'
                      : error is ApiFailure && error.status == 404
                      ? 'Không tìm thấy thông tin'
                      : 'Chưa tải được thông tin')}',
          ),
          if (showId)
            SelectableText(id, style: Theme.of(context).textTheme.bodySmall),
          if (value?.hasError == true &&
              !(value!.error is ApiFailure &&
                  const [
                    401,
                    403,
                    404,
                  ].contains((value.error as ApiFailure).status)))
            TextButton(
              onPressed: () =>
                  ref.invalidate(pharmacyIdentityNameProvider((kind, id))),
              child: Text('Tra cứu $label lại'),
            ),
        ],
      ),
    );
  }
}
