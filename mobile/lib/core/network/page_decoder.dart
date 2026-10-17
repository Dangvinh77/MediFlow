import '../domain/page_result.dart';

PageResult<T> decodePage<T>(
  Object? value,
  T Function(Map<String, dynamic>) decode,
) {
  if (value is! Map<String, dynamic> ||
      value['content'] is! List ||
      value['number'] is! int ||
      value['totalPages'] is! int ||
      value['totalElements'] is! int ||
      (value['number'] as int) < 0 ||
      (value['totalPages'] as int) < 0 ||
      (value['totalElements'] as int) < 0) {
    throw const FormatException('Phản hồi phân trang không hợp lệ.');
  }
  return PageResult(
    items: (value['content'] as List)
        .map((item) {
          if (item is! Map<String, dynamic>) {
            throw const FormatException('Dữ liệu dòng không hợp lệ.');
          }
          return decode(item);
        })
        .toList(growable: false),
    number: value['number'] as int,
    totalPages: value['totalPages'] as int,
    totalElements: value['totalElements'] as int,
  );
}
