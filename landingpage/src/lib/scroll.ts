export function getHeaderOffset(): number {
  const raw = getComputedStyle(document.documentElement)
    .getPropertyValue("--header-offset")
    .trim();
  const rem = parseFloat(raw);
  if (Number.isNaN(rem)) return 88;
  const rootFont = parseFloat(getComputedStyle(document.documentElement).fontSize);
  return rem * rootFont;
}

export function prefersReducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function scrollToSection(
  id: string,
  options?: { smooth?: boolean }
): void {
  const sectionId = id.replace(/^#/, "");
  const element = document.getElementById(sectionId);
  if (!element) return;

  const offset = getHeaderOffset();
  const top =
    element.getBoundingClientRect().top + window.scrollY - offset + 1;
  const smooth =
    options?.smooth !== false && !prefersReducedMotion();

  window.scrollTo({
    top: Math.max(0, top),
    behavior: smooth ? "smooth" : "auto",
  });
}
