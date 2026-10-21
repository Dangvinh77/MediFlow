class Drug {
  const Drug({
    required this.id,
    required this.name,
    required this.activeIngredient,
    required this.unit,
    required this.price,
    required this.stockQuantity,
    required this.expiryDate,
    required this.manufacturer,
    required this.lowStockThreshold,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id, name, unit, expiryDate;
  final String? activeIngredient, manufacturer;
  // Preserve the server number. Client display is not a pricing authority.
  final num price;
  final int stockQuantity, lowStockThreshold;
  final DateTime createdAt, updatedAt;
  bool get isLowStock =>
      stockQuantity > 0 && stockQuantity <= lowStockThreshold;
}
