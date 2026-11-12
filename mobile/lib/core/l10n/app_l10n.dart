import 'package:flutter/material.dart';

import 'messages_en.dart';
import 'messages_vi.dart';

class AppL10n extends InheritedWidget {
  const AppL10n({super.key, required this.language, required super.child});
  final String language;
  static String languageOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppL10n>()?.language ?? 'vi';
  static T text<T extends String?>(BuildContext context, T source) =>
      (source == null ? null : translate(source, languageOf(context))) as T;
  @override
  bool updateShouldNotify(AppL10n oldWidget) => language != oldWidget.language;
}

final _patterns = messagesEn.entries
    .where((entry) => RegExp(r'\{\d+\}').hasMatch(entry.key))
    .map((entry) {
      final slots = <String>[];
      final pattern = entry.key.splitMapJoin(
        RegExp(r'\{\d+\}'),
        onMatch: (match) {
          slots.add(match[0]!);
          return '(.+?)';
        },
        onNonMatch: RegExp.escape,
      );
      return (
        regex: RegExp('^$pattern\$', dotAll: true),
        target: entry.value,
        slots: slots,
      );
    })
    .toList();

String translate(String source, String language) {
  final trimmed = source.trim();
  if (language != 'en') {
    final value = messagesVi[trimmed];
    return value == null || value == trimmed
        ? source
        : source.replaceFirst(trimmed, value);
  }
  final direct = messagesEn[trimmed];
  if (direct != null) return source.replaceFirst(trimmed, direct);
  for (final item in _patterns) {
    final match = item.regex.firstMatch(trimmed);
    if (match == null) continue;
    final value = item.target.replaceAllMapped(RegExp(r'\{\d+\}'), (slot) {
      final index = item.slots.indexOf(slot[0]!);
      return index < 0 ? slot[0]! : match.group(index + 1)!;
    });
    return source.replaceFirst(trimmed, value);
  }
  return source;
}

/// A Text widget for application copy. Patient-authored content uses ordinary Text.
class LText extends StatelessWidget {
  final String data;
  final TextStyle? style;
  final StrutStyle? strutStyle;
  final TextAlign? textAlign;
  final TextDirection? textDirection;
  final Locale? locale;
  final bool? softWrap;
  final TextOverflow? overflow;
  final TextScaler? textScaler;
  final int? maxLines;
  final String? semanticsLabel;
  final TextWidthBasis? textWidthBasis;
  final TextHeightBehavior? textHeightBehavior;
  final Color? selectionColor;
  const LText(
    this.data, {
    super.key,
    this.style,
    this.strutStyle,
    this.textAlign,
    this.textDirection,
    this.locale,
    this.softWrap,
    this.overflow,
    this.textScaler,
    this.maxLines,
    this.semanticsLabel,
    this.textWidthBasis,
    this.textHeightBehavior,
    this.selectionColor,
  });
  @override
  Widget build(BuildContext context) => Text(
    AppL10n.text(context, data),
    style: style,
    strutStyle: strutStyle,
    textAlign: textAlign,
    textDirection: textDirection,
    locale: locale,
    softWrap: softWrap,
    overflow: overflow,
    textScaler: textScaler,
    maxLines: maxLines,
    semanticsLabel: semanticsLabel,
    textWidthBasis: textWidthBasis,
    textHeightBehavior: textHeightBehavior,
    selectionColor: selectionColor,
  );
}

class LNavigationDestination extends NavigationDestination {
  const LNavigationDestination({
    super.key,
    required super.icon,
    super.selectedIcon,
    required super.label,
    super.tooltip,
    super.enabled,
  });
  @override
  Widget build(BuildContext context) => NavigationDestination(
    icon: icon,
    selectedIcon: selectedIcon,
    label: AppL10n.text(context, label),
    tooltip: tooltip == null ? null : AppL10n.text(context, tooltip!),
    enabled: enabled,
  );
}
