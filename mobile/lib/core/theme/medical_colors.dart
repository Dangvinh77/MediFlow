import 'package:flutter/material.dart';

/// Brand constants shared by the theme and the painted brand elements.
abstract final class MediFlowBrand {
  /// Scrub teal; the same value the loader and web manifest already use.
  static const scrub = Color(0xFF0F766E);

  /// Deep clinical teal behind the app bar, hero and login header.
  static const deep = Color(0xFF083F45);

  /// The monitor trace colour, shared with the loader's trail.
  static const pulse = Color(0xFF2DD4BF);
}

/// Hospital wayfinding: every care area keeps one colour, the way a hospital
/// paints a line on the floor for each department. A colour here always means
/// "this area of the app", never a status. Status colours live in
/// [MedicalColors].
abstract final class Wayfinding {
  static const home = MediFlowBrand.scrub;
  static const patient = Color(0xFF0E7C86);
  static const appointment = Color(0xFF1D5FC4);
  static const record = Color(0xFF4347B0);
  static const lab = Color(0xFF7A35B0);
  static const pharmacy = Color(0xFF1A7F50);
  static const billing = Color(0xFFA85D00);
  static const support = Color(0xFF1F7396);
  static const organization = Color(0xFF4A6474);
}

/// Semantic colours Material's scheme has no slot for. Status follows the
/// triage convention clinicians already read: red, amber, green, blue.
@immutable
class MedicalColors extends ThemeExtension<MedicalColors> {
  const MedicalColors({
    required this.success,
    required this.warning,
    required this.danger,
    required this.info,
    required this.mutedText,
    required this.hairline,
    required this.isDark,
  });

  final Color success, warning, danger, info, mutedText, hairline;
  final bool isDark;

  static const light = MedicalColors(
    success: Color(0xFF17754A),
    warning: Color(0xFF9A5200),
    danger: Color(0xFFC02A37),
    info: Color(0xFF1D5FC4),
    mutedText: Color(0xFF4A6467),
    hairline: Color(0xFFD5E4E4),
    isDark: false,
  );

  static const dark = MedicalColors(
    success: Color(0xFF5AD19A),
    warning: Color(0xFFF2B35E),
    danger: Color(0xFFFF8A8F),
    info: Color(0xFF8DB8F7),
    mutedText: Color(0xFFA3BDBF),
    hairline: Color(0xFF23434A),
    isDark: true,
  );

  /// Lifts a wayfinding colour so it stays readable on the dark surface.
  Color accent(Color color) {
    if (!isDark) return color;
    final hsl = HSLColor.fromColor(color);
    return hsl.withLightness(0.74).withSaturation(0.62).toColor();
  }

  /// A quiet wash of [color] for tiles, badges and pills.
  Color wash(Color color) =>
      accent(color).withValues(alpha: isDark ? 0.16 : 0.09);

  @override
  MedicalColors copyWith({
    Color? success,
    Color? warning,
    Color? danger,
    Color? info,
    Color? mutedText,
    Color? hairline,
    bool? isDark,
  }) => MedicalColors(
    success: success ?? this.success,
    warning: warning ?? this.warning,
    danger: danger ?? this.danger,
    info: info ?? this.info,
    mutedText: mutedText ?? this.mutedText,
    hairline: hairline ?? this.hairline,
    isDark: isDark ?? this.isDark,
  );

  @override
  MedicalColors lerp(ThemeExtension<MedicalColors>? other, double t) {
    if (other is! MedicalColors) return this;
    return MedicalColors(
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      info: Color.lerp(info, other.info, t)!,
      mutedText: Color.lerp(mutedText, other.mutedText, t)!,
      hairline: Color.lerp(hairline, other.hairline, t)!,
      isDark: t < 0.5 ? isDark : other.isDark,
    );
  }
}

extension MedicalThemeContext on BuildContext {
  /// Falls back by brightness so a widget also renders under a plain
  /// [ThemeData], as it does in isolated widget tests.
  MedicalColors get medical {
    final theme = Theme.of(this);
    return theme.extension<MedicalColors>() ??
        (theme.brightness == Brightness.dark
            ? MedicalColors.dark
            : MedicalColors.light);
  }
}
