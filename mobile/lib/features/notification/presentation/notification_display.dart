String notificationChannelLabel(String channel) => switch (channel) {
  'IN_APP' => 'Trong ứng dụng',
  'EMAIL' => 'Email',
  'SMS' => 'Tin nhắn SMS',
  _ => 'Kênh gửi chưa xác định',
};

String notificationStatusLabel(String status) => switch (status) {
  'PENDING' => 'Đang chờ gửi',
  'SENT' => 'Đã gửi',
  'FAILED' => 'Gửi thất bại',
  _ => 'Trạng thái chưa xác định',
};

String notificationTimeLabel(String raw) {
  final value = DateTime.tryParse(raw)?.toLocal();
  if (value == null) return 'Chưa rõ thời gian';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(value.hour)}:${two(value.minute)} '
      '${two(value.day)}/${two(value.month)}/${value.year}';
}

String notificationFailureLabel(String? reason) =>
    'Thông báo chưa gửi được. Vui lòng kiểm tra thông tin người nhận rồi thử lại.';

final _rawAmount = RegExp(
  r'(?<![\d.,])(\d{4,})(?:\.(\d{1,2}))?(?=\s?(?:VND|VNĐ|đ)(?![\p{L}\d]))',
  unicode: true,
);

/// Older stored messages carry raw amounts ("36000 VND"); show them grouped (36.000 VND).
String notificationText(String value) =>
    value.replaceAllMapped(_rawAmount, (match) {
      final integer = match
          .group(1)!
          .replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => '.');
      final fraction = (match.group(2) ?? '').replaceFirst(RegExp(r'0+$'), '');
      return fraction.isEmpty ? integer : '$integer,$fraction';
    });
