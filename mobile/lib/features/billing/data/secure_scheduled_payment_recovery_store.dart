import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../domain/billing_models.dart';
import '../domain/scheduled_payments.dart';

class SecureScheduledPaymentRecoveryStore
    implements ScheduledPaymentRecoveryStore {
  SecureScheduledPaymentRecoveryStore(
    this.storage, {
    required String accountId,
  }) : accountId = accountId.toLowerCase() {
    if (!isBillingUuid(this.accountId)) {
      throw ArgumentError.value(
        accountId,
        'accountId',
        'Expected an account UUID.',
      );
    }
  }

  final FlutterSecureStorage storage;
  final String accountId;

  @override
  Future<ScheduledPaymentRecovery?> read(String paymentRequestId) async {
    _requireUuid(paymentRequestId);
    final value = await storage.read(key: _key(accountId, paymentRequestId));
    if (value == null) return null;
    final decoded = jsonDecode(value);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Yêu cầu thu đã lưu không hợp lệ.');
    }
    return _fromJson(decoded);
  }

  @override
  Future<void> save(
    String paymentRequestId,
    ScheduledPaymentRecovery recovery,
  ) async {
    _requireUuid(paymentRequestId);
    if (!isBillingUuid(recovery.idempotencyKey) ||
        !isBillingUuid(recovery.correlationId) ||
        recovery.operationId != null && !isBillingUuid(recovery.operationId!)) {
      throw ArgumentError('Mã đối soát yêu cầu thu không hợp lệ.');
    }
    await storage.write(
      key: _key(accountId, paymentRequestId),
      value: jsonEncode({
        'kind': recovery.kind.name,
        'idempotencyKey': recovery.idempotencyKey,
        'correlationId': recovery.correlationId,
        'operationId': recovery.operationId,
        'paymentCommand': recovery.paymentCommand == null
            ? null
            : _paymentCommandJson(recovery.paymentCommand!),
        'zeroCommand': recovery.zeroCommand == null
            ? null
            : _zeroCommandJson(recovery.zeroCommand!),
      }),
    );
  }

  @override
  Future<void> clear(String paymentRequestId) async {
    _requireUuid(paymentRequestId);
    await storage.delete(key: _key(accountId, paymentRequestId));
  }

  static String _key(String accountId, String paymentRequestId) =>
      'mediflow.billing.scheduled-payment.v3.$accountId.$paymentRequestId';

  static ScheduledPaymentRecovery _fromJson(Map<String, dynamic> json) {
    final kind = _string(json, 'kind');
    final payment = json['paymentCommand'];
    final zero = json['zeroCommand'];
    final operationId = _nullableString(json, 'operationId');
    if (operationId != null && !isBillingUuid(operationId)) {
      throw const FormatException('Mã thao tác đã lưu không hợp lệ.');
    }
    final recoveryKind = switch (kind) {
      'payment' => ScheduledPaymentRecoveryKind.payment,
      'zeroLiability' => ScheduledPaymentRecoveryKind.zeroLiability,
      _ => throw const FormatException('Loại yêu cầu thu đã lưu không hợp lệ.'),
    };
    final paymentCommand = payment == null
        ? null
        : _paymentCommand(_object(payment));
    final zeroCommand = zero == null ? null : _zeroCommand(_object(zero));
    if (recoveryKind == ScheduledPaymentRecoveryKind.payment &&
            (paymentCommand == null || zeroCommand != null) ||
        recoveryKind == ScheduledPaymentRecoveryKind.zeroLiability &&
            (zeroCommand == null || paymentCommand != null)) {
      throw const FormatException('Nội dung yêu cầu thu đã lưu không khớp.');
    }
    return ScheduledPaymentRecovery(
      kind: recoveryKind,
      idempotencyKey: _uuid(json, 'idempotencyKey'),
      correlationId: _uuid(json, 'correlationId'),
      paymentCommand: paymentCommand,
      zeroCommand: zeroCommand,
      operationId: operationId,
    );
  }

  static ScheduledPaymentCommand _paymentCommand(Map<String, dynamic> json) {
    final command = ScheduledPaymentCommand(
      reservationId: _uuid(json, 'reservationId'),
      expectedReservationRevision: _integer(
        json,
        'expectedReservationRevision',
      ),
      quoteId: _nullableUuid(json, 'quoteId'),
      amount: _string(json, 'amount'),
      currency: _string(json, 'currency'),
      paymentMethod: _string(json, 'paymentMethod'),
      providerReference: _nullableString(json, 'providerReference'),
    );
    if (command.expectedReservationRevision < 1 ||
        !_validPositiveMoney(command.amount) ||
        command.currency != 'VND' ||
        !const {'CASH', 'BANK_TRANSFER'}.contains(command.paymentMethod) ||
        command.paymentMethod == 'CASH' &&
            command.providerReference != null ||
        command.paymentMethod == 'BANK_TRANSFER' &&
            (command.providerReference == null ||
                command.providerReference!.trim().isEmpty ||
                command.providerReference!.length > 120)) {
      throw const FormatException('Lệnh thu đã lưu không hợp lệ.');
    }
    return command;
  }

  static ScheduledZeroLiabilityCommand _zeroCommand(Map<String, dynamic> json) {
    final command = ScheduledZeroLiabilityCommand(
      reservationId: _uuid(json, 'reservationId'),
      expectedReservationRevision: _integer(
        json,
        'expectedReservationRevision',
      ),
      quoteId: _uuid(json, 'quoteId'),
    );
    if (command.expectedReservationRevision < 1) {
      throw const FormatException('Lệnh xác nhận nghĩa vụ 0 không hợp lệ.');
    }
    return command;
  }

  static Map<String, Object?> _paymentCommandJson(
    ScheduledPaymentCommand command,
  ) => {
    'reservationId': command.reservationId,
    'expectedReservationRevision': command.expectedReservationRevision,
    'quoteId': command.quoteId,
    'amount': command.amount,
    'currency': command.currency,
    'paymentMethod': command.paymentMethod,
    'providerReference': command.providerReference,
  };

  static Map<String, Object?> _zeroCommandJson(
    ScheduledZeroLiabilityCommand command,
  ) => {
    'reservationId': command.reservationId,
    'expectedReservationRevision': command.expectedReservationRevision,
    'quoteId': command.quoteId,
  };

  static Map<String, dynamic> _object(Object value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Yêu cầu thu đã lưu không hợp lệ.');
    }
    return value;
  }

  static String _string(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! String || value.isEmpty) {
      throw FormatException('Dữ liệu lưu $key không hợp lệ.');
    }
    return value;
  }

  static String? _nullableString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value != null && value is! String) {
      throw FormatException('Dữ liệu lưu $key không hợp lệ.');
    }
    return value as String?;
  }

  static int _integer(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! int) throw FormatException('Dữ liệu lưu $key không hợp lệ.');
    return value;
  }

  static String _uuid(Map<String, dynamic> json, String key) {
    final value = _string(json, key);
    if (!isBillingUuid(value)) {
      throw FormatException('Dữ liệu lưu $key không hợp lệ.');
    }
    return value;
  }

  static String? _nullableUuid(Map<String, dynamic> json, String key) {
    final value = _nullableString(json, key);
    if (value != null && !isBillingUuid(value)) {
      throw FormatException('Dữ liệu lưu $key không hợp lệ.');
    }
    return value;
  }

  static bool _validPositiveMoney(String value) =>
      RegExp(r'^(0|[1-9]\d{0,16})(?:\.\d{1,2})?$').hasMatch(value) &&
      value != '0' &&
      value != '0.0' &&
      value != '0.00';

  static void _requireUuid(String value) {
    if (!isBillingUuid(value)) throw ArgumentError('Mã phải là UUID hợp lệ.');
  }
}
