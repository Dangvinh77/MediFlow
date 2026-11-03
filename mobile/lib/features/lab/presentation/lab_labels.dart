String labStatusLabel(String value) => switch (value) {
  'PENDING' => 'Chờ xử lý',
  'AWAITING_PAYMENT' => 'Chờ xác nhận thanh toán',
  'READY' => 'Sẵn sàng thực hiện',
  'IN_PROGRESS' => 'Đang thực hiện',
  'COMPLETED' => 'Đã có kết quả',
  'CANCELLED' => 'Đã hủy',
  _ => 'Chưa xác định trạng thái',
};

String labDateLabel(String value) {
  final date = DateTime.tryParse(value);
  return date == null
      ? value
      : '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
}
