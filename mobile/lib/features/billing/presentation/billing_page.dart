import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../domain/billing_models.dart';
import '../domain/scheduled_payments.dart';
import 'billing_reports_page.dart';
import 'invoice_page.dart';
import 'ledger_payment_page.dart';
import 'providers.dart';
import 'payment_queue_page.dart';
import 'scheduled_payment_page.dart';

class BillingPage extends ConsumerWidget {
  const BillingPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(billingRepositoryProvider);
    if (access == null || repository == null) {
      return const Center(child: LText('Chức năng Viện phí chưa sẵn sàng.'));
    }
    if (!const {'ADMIN', 'CASHIER'}.contains(access.role)) {
      return const Center(child: LText('Bạn không có quyền xem Viện phí.'));
    }
    final config = ref.watch(appConfigProvider);
    final scheduled = ref.watch(scheduledPaymentRepositoryProvider);
    final scheduledConfigured =
        (access.role == 'ADMIN' || access.role == 'CASHIER') &&
        config.scheduledPaymentsEnabled &&
        scheduled != null;
    final recovery = scheduledConfigured
        ? ref.watch(scheduledPaymentRecoveryStoreProvider)
        : null;
    final scheduledEnabled = scheduledConfigured && recovery != null;
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, access.role, repository)),
      child: _BillingWorkspace(
        repository: repository,
        role: access.role,
        scheduledRepository: scheduledEnabled ? scheduled : null,
        recoveryStore: scheduledEnabled ? recovery : null,
        scheduledEnabled: scheduledEnabled,
        quoteEnabled: config.outpatientQuotesEnabled,
        zeroPayableEnabled: config.zeroPayableEnabled,
      ),
    );
  }
}

class _BillingWorkspace extends ConsumerWidget {
  const _BillingWorkspace({
    required this.repository,
    required this.role,
    required this.scheduledRepository,
    required this.recoveryStore,
    required this.scheduledEnabled,
    required this.quoteEnabled,
    required this.zeroPayableEnabled,
  });
  final BillingRepository repository;
  final String role;
  final ScheduledPaymentRepository? scheduledRepository;
  final ScheduledPaymentRecoveryStore? recoveryStore;
  final bool scheduledEnabled, quoteEnabled, zeroPayableEnabled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final queue = ref.watch(paymentQueueRepositoryProvider);
    final canOperate = role == 'ADMIN' || role == 'CASHIER';
    final canReport = role == 'ADMIN';
    final labQuotes = quoteEnabled
        ? ref.watch(labQuotePaymentRepositoryProvider)
        : null;
    final labRecovery = quoteEnabled
        ? ref.watch(labPaymentRecoveryStoreProvider)
        : null;
    final tabs = <Widget>[], pages = <Widget>[];
    if (canOperate) {
      tabs.add(const Tab(text: 'Xét nghiệm và thuốc'));
      pages.add(
        queue == null
            ? LedgerPaymentPage(
                repository: repository,
                labQuotes: labQuotes,
                labRecovery: labRecovery,
                labZeroEnabled: zeroPayableEnabled,
              )
            : PaymentQueuePage(
                repository: repository,
                queue: queue,
                labQuotes: labQuotes,
                labRecovery: labRecovery,
                labZeroEnabled: zeroPayableEnabled,
              ),
      );
      if (scheduledEnabled &&
          scheduledRepository != null &&
          recoveryStore != null) {
        tabs.add(const Tab(text: 'Phí khám'));
        pages.add(
          ScheduledPaymentQueuePage(
            repository: scheduledRepository!,
            recoveryStore: recoveryStore!,
            quoteEnabled: quoteEnabled,
            zeroPayableEnabled: zeroPayableEnabled,
          ),
        );
      }
      tabs.add(const Tab(text: 'Lịch sử thu'));
      pages.add(InvoicePage(repository: repository));
    }
    if (canReport) {
      tabs.add(const Tab(text: 'Báo cáo'));
      pages.add(BillingReportsPage(repository: repository, role: role));
    }
    return DefaultTabController(
      initialIndex: 0,
      length: tabs.length,
      child: Column(
        children: [
          TabBar(isScrollable: true, tabs: tabs),
          Expanded(child: TabBarView(children: pages)),
        ],
      ),
    );
  }
}
