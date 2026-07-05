/**
 * Replaces em/en dashes in user-facing copy with standard English punctuation.
 * Use commas for brief pauses; use periods when starting a new clause.
 */
export function normalizeCopyText(text: string): string {
  return text
    .replace(/\s*\u2014\s*/g, ", ")
    .replace(/\s*\u2013\s*/g, ", ")
    .replace(/,\s*,/g, ",")
    .replace(/,\s+\./g, ".")
    .replace(/\s{2,}/g, " ")
    .trim();
}

export function normalizeCopyTree<T>(value: T): T {
  if (typeof value === "string") {
    return normalizeCopyText(value) as T;
  }

  if (Array.isArray(value)) {
    return value.map((item) => normalizeCopyTree(item)) as T;
  }

  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, nested]) => [key, normalizeCopyTree(nested)])
    ) as T;
  }

  return value;
}
