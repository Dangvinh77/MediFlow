import 'package:flutter/material.dart';

class FeatureWorkspace extends StatelessWidget {
  const FeatureWorkspace({super.key, required this.title});
  final String title;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.construction_outlined, size: 48),
          const SizedBox(height: 16),
          Text(title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text(
            'Chức năng trên ứng dụng di động đang được phát triển.',
            textAlign: TextAlign.center,
          ),
        ],
      ),
    ),
  );
}
