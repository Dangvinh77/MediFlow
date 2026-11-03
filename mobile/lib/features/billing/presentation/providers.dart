import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/billing_models.dart';
import '../domain/payment_queue.dart';

final billingRepositoryProvider = Provider<BillingRepository?>((ref) => null);
final outpatientRefundRepositoryProvider =
    Provider<OutpatientRefundRepository?>((ref) => null);
final paymentQueueRepositoryProvider = Provider<PaymentQueueRepository?>(
  (ref) => null,
);
