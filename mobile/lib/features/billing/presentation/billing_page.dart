import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../domain/billing_models.dart';
import 'billing_reports_page.dart';
import 'invoice_page.dart';
import 'ledger_payment_page.dart';
import 'providers.dart';
import 'refund_due_page.dart';

class BillingPage extends ConsumerWidget {
  const BillingPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(billingRepositoryProvider);
    if (access == null || repository == null) {
      return const Center(child: Text('Chức năng Viện phí chưa sẵn sàng.'));
    }
    if (!const {'ADMIN', 'CASHIER', 'MANAGER'}.contains(access.role)) {
      return const Center(child: Text('Bạn không có quyền xem Viện phí.'));
    }
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, access.role, repository)),
      child: _BillingWorkspace(repository: repository, role: access.role),
    );
  }
}

class _BillingWorkspace extends StatelessWidget {
  const _BillingWorkspace({required this.repository, required this.role});
  final BillingRepository repository;
  final String role;

  @override
  Widget build(BuildContext context) {
    final canOperate = role == 'ADMIN' || role == 'CASHIER';
    return DefaultTabController(
      length: canOperate ? 3 + (role == 'ADMIN' ? 1 : 0) : 1,
      child: Column(
        children: [
          TabBar(
            isScrollable: true,
            tabs: [
              if (canOperate) const Tab(text: 'Hóa đơn'),
              if (canOperate) const Tab(text: 'Khoản thu'),
              if (canOperate) const Tab(text: 'Hoàn tiền'),
              if (role == 'ADMIN' || role == 'MANAGER')
                const Tab(text: 'Báo cáo'),
            ],
          ),
          Expanded(
            child: TabBarView(
              children: [
                if (canOperate) InvoicePage(repository: repository),
                if (canOperate) LedgerPaymentPage(repository: repository),
                if (canOperate) RefundDuePage(repository: repository),
                if (role == 'ADMIN' || role == 'MANAGER')
                  BillingReportsPage(repository: repository, role: role),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
