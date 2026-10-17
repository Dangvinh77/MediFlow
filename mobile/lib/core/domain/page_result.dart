class PageResult<T> {
  const PageResult({
    required this.items,
    required this.number,
    required this.totalPages,
    required this.totalElements,
  });
  final List<T> items;
  final int number;
  final int totalPages;
  final int totalElements;
  bool get hasNext => number + 1 < totalPages;
}
