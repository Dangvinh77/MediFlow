const UUID_PATTERN = /[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}/i;

export function containsUuid(value: string): boolean {
  return UUID_PATTERN.test(value);
}
