import 'package:flutter/material.dart';

import 'medical_colors.dart';

/// The one place the app's look is decided. Feature pages use stock Material
/// widgets, so they pick all of this up without per-page styling.
ThemeData buildAppTheme(Brightness brightness) {
  final dark = brightness == Brightness.dark;
  final medical = dark ? MedicalColors.dark : MedicalColors.light;
  final scheme =
      ColorScheme.fromSeed(
        seedColor: MediFlowBrand.scrub,
        brightness: brightness,
      ).copyWith(
        primary: dark ? const Color(0xFF5EDDCB) : MediFlowBrand.scrub,
        onPrimary: dark ? const Color(0xFF04302F) : Colors.white,
        primaryContainer: dark
            ? const Color(0xFF0F4A4C)
            : const Color(0xFFCFF1EB),
        onPrimaryContainer: dark
            ? const Color(0xFFBFF3EA)
            : const Color(0xFF06403C),
        secondary: dark ? const Color(0xFF8DB8F7) : const Color(0xFF1D5FC4),
        onSecondary: dark ? const Color(0xFF08274F) : Colors.white,
        error: medical.danger,
        surface: dark ? const Color(0xFF0E2529) : Colors.white,
        onSurface: dark ? const Color(0xFFE3F0F0) : const Color(0xFF0F2C30),
        onSurfaceVariant: medical.mutedText,
        surfaceContainerLowest: dark
            ? const Color(0xFF0A1D21)
            : const Color(0xFFF1F7F7),
        surfaceContainerLow: dark
            ? const Color(0xFF12292E)
            : const Color(0xFFEDF5F5),
        surfaceContainer: dark
            ? const Color(0xFF163136)
            : const Color(0xFFE6F0F0),
        surfaceContainerHigh: dark
            ? const Color(0xFF1B3A40)
            : const Color(0xFFDFEBEB),
        outline: dark ? const Color(0xFF5C7C80) : const Color(0xFF7E9A9C),
        outlineVariant: medical.hairline,
      );

  final base = ThemeData(useMaterial3: true, colorScheme: scheme);
  final text = base.textTheme;
  final textTheme = text.copyWith(
    headlineLarge: text.headlineLarge?.copyWith(
      fontWeight: FontWeight.w800,
      letterSpacing: -0.6,
    ),
    headlineMedium: text.headlineMedium?.copyWith(
      fontSize: 26,
      fontWeight: FontWeight.w800,
      letterSpacing: -0.4,
    ),
    headlineSmall: text.headlineSmall?.copyWith(
      fontWeight: FontWeight.w700,
      letterSpacing: -0.2,
    ),
    titleLarge: text.titleLarge?.copyWith(
      fontSize: 20,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.1,
    ),
    titleMedium: text.titleMedium?.copyWith(fontWeight: FontWeight.w600),
    titleSmall: text.titleSmall?.copyWith(fontWeight: FontWeight.w600),
    bodyMedium: text.bodyMedium?.copyWith(height: 1.45),
    bodySmall: text.bodySmall?.copyWith(height: 1.4),
    labelLarge: text.labelLarge?.copyWith(fontWeight: FontWeight.w600),
  );

  OutlineInputBorder field(Color color, [double width = 1]) =>
      OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: color, width: width),
      );
  final buttonShape = RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(12),
  );
  const buttonSize = Size(64, 46);

  return base.copyWith(
    textTheme: textTheme,
    extensions: [medical],
    scaffoldBackgroundColor: scheme.surfaceContainerLowest,
    dividerTheme: DividerThemeData(color: medical.hairline, space: 1),
    // The shell header is deep teal; pages pushed inside it get this quieter
    // bar so the two levels read as parent and child.
    appBarTheme: AppBarTheme(
      backgroundColor: scheme.surface,
      foregroundColor: scheme.onSurface,
      elevation: 0,
      scrolledUnderElevation: 0,
      surfaceTintColor: Colors.transparent,
      centerTitle: false,
      shape: Border(bottom: BorderSide(color: medical.hairline)),
      titleTextStyle: textTheme.titleMedium?.copyWith(
        color: scheme.onSurface,
        fontSize: 17,
      ),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: scheme.surface,
      surfaceTintColor: Colors.transparent,
      margin: const EdgeInsets.symmetric(vertical: 4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: medical.hairline),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: scheme.surface,
      border: field(scheme.outline),
      enabledBorder: field(medical.hairline, 1.2),
      focusedBorder: field(scheme.primary, 2),
      errorBorder: field(medical.danger, 1.2),
      focusedErrorBorder: field(medical.danger, 2),
      disabledBorder: field(medical.hairline),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: buttonSize,
        shape: buttonShape,
        textStyle: textTheme.labelLarge,
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        minimumSize: buttonSize,
        shape: buttonShape,
        elevation: 0,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: buttonSize,
        shape: buttonShape,
        side: BorderSide(color: scheme.primary.withValues(alpha: 0.5)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        shape: buttonShape,
        textStyle: textTheme.labelLarge,
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: scheme.primary,
      foregroundColor: scheme.onPrimary,
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: scheme.surfaceContainerLow,
      selectedColor: scheme.primaryContainer,
      side: BorderSide(color: medical.hairline),
      shape: const StadiumBorder(),
      labelStyle: textTheme.labelMedium,
    ),
    listTileTheme: ListTileThemeData(
      iconColor: scheme.primary,
      selectedColor: scheme.primary,
      selectedTileColor: scheme.primaryContainer.withValues(alpha: 0.5),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    expansionTileTheme: ExpansionTileThemeData(
      iconColor: scheme.primary,
      textColor: scheme.primary,
      shape: const Border(),
      collapsedShape: const Border(),
    ),
    tabBarTheme: TabBarThemeData(
      labelColor: scheme.primary,
      unselectedLabelColor: medical.mutedText,
      indicatorColor: scheme.primary,
      dividerColor: medical.hairline,
      labelStyle: textTheme.labelLarge,
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 68,
      elevation: 0,
      backgroundColor: scheme.surface,
      surfaceTintColor: Colors.transparent,
      indicatorColor: scheme.primaryContainer,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
    ),
    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: scheme.surface,
    ),
    drawerTheme: DrawerThemeData(
      backgroundColor: scheme.surface,
      surfaceTintColor: Colors.transparent,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.horizontal(right: Radius.circular(24)),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: scheme.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: scheme.surface,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: scheme.primaryContainer,
    ),
  );
}
