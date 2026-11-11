String formatClinicalInstant(String value) {
  try {
    final local = DateTime.parse(value).toUtc().add(const Duration(hours: 7));
    final year = local.year.toString().padLeft(4, '0');
    final month = local.month.toString().padLeft(2, '0');
    final day = local.day.toString().padLeft(2, '0');
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '$year-$month-$day $hour:$minute (GMT+7)';
  } on FormatException {
    return value;
  }
}
