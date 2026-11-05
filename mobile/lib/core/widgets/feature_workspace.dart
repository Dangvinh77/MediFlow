import 'package:flutter/material.dart';

import '../theme/medical_colors.dart';
import 'medical_ui.dart';

class FeatureWorkspace extends StatelessWidget {
  const FeatureWorkspace({super.key, required this.title});
  final String title;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: StateMessage(
        icon: Icons.construction_outlined,
        color: Wayfinding.organization,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            const Text(
              'Chức năng trên ứng dụng di động đang được phát triển.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    ),
  );
}
