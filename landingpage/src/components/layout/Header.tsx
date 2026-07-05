"use client";

import { Menu, X } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui";
import { GITHUB_REPO } from "@/lib/links";
import { scrollToSection } from "@/lib/scroll";
import { cn } from "@/lib/utils";

const navLinks = [
  { label: "Features", href: "#features" },
  { label: "Cards", href: "#cards" },
  { label: "Transfer", href: "#transfer" },
  { label: "Bills", href: "#bills" },
];

const SCROLL_THRESHOLD = 10;
const DIRECTION_DELTA = 6;

function isDesktopViewport() {
  return window.matchMedia("(min-width: 1024px)").matches;
}

export function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [visible, setVisible] = useState(true);
  const lastScrollY = useRef(0);
  const ticking = useRef(false);

  const closeMobile = useCallback(() => {
    setMobileOpen(false);
    document.body.style.overflow = "";
  }, []);

  useEffect(() => {
    if (mobileOpen) {
      setVisible(true);
    }
  }, [mobileOpen]);

  useEffect(() => {
    const updateScrollState = () => {
      const currentY = window.scrollY;
      const delta = currentY - lastScrollY.current;

      setScrolled(currentY > SCROLL_THRESHOLD);

      // Keep header visible on mobile/tablet and while the menu is open
      if (
        currentY <= SCROLL_THRESHOLD ||
        mobileOpen ||
        !isDesktopViewport()
      ) {
        setVisible(true);
      } else if (delta > DIRECTION_DELTA) {
        setVisible(false);
      } else if (delta < -DIRECTION_DELTA) {
        setVisible(true);
      }

      lastScrollY.current = currentY;
      ticking.current = false;
    };

    const onScroll = () => {
      if (!ticking.current) {
        ticking.current = true;
        requestAnimationFrame(updateScrollState);
      }
    };

    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, [mobileOpen]);

  useEffect(() => {
    document.body.style.overflow = mobileOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileOpen]);

  useEffect(() => {
    if (!mobileOpen) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeMobile();
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [mobileOpen, closeMobile]);

  const handleNavClick = useCallback(
    (href: string) => (event: React.MouseEvent<HTMLAnchorElement>) => {
      if (!href.startsWith("#")) return;
      event.preventDefault();
      setVisible(true);
      closeMobile();
      requestAnimationFrame(() => scrollToSection(href));
    },
    [closeMobile]
  );

  const toggleMobile = () => {
    setMobileOpen((open) => {
      if (!open) setVisible(true);
      return !open;
    });
  };

  return (
    <header
      className={cn(
        "pointer-events-none fixed inset-x-0 top-0 z-50 pt-3 transition-transform duration-300 ease-in-out md:pt-4",
        !visible && "-translate-y-[calc(100%+0.75rem)]"
      )}
    >
      <div className="container mx-auto max-w-7xl px-4 md:px-6 lg:px-8">
        <div
          className={cn(
            "pointer-events-auto w-full border border-slate-200/80 bg-white transition-[border-radius,box-shadow] duration-300",
            mobileOpen ? "overflow-visible rounded-[1.5rem]" : "overflow-hidden rounded-full",
            scrolled
              ? "shadow-[0_2px_8px_rgba(22,42,44,0.06)]"
              : "shadow-[0_1px_2px_rgba(22,42,44,0.04)]"
          )}
        >
          <div className="flex h-16 items-center justify-between px-4 md:h-[4.5rem] md:px-6 lg:px-8">
            <Link
              href="/"
              className="text-[1.125rem] font-semibold tracking-tight text-[#162A2C] md:text-[1.2rem]"
              onClick={closeMobile}
            >
              <span className="text-[#CC1E1E]">elvo</span>
              digital
            </Link>

            <nav className="hidden items-center gap-7 lg:flex" aria-label="Main">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="interact-link text-[14px] font-medium text-slate-600"
                  onClick={handleNavClick(link.href)}
                >
                  {link.label}
                </Link>
              ))}
            </nav>

            <div className="hidden items-center gap-3 lg:flex">
              <Button href={GITHUB_REPO} external>
                Github Repository
              </Button>
            </div>

            <button
              type="button"
              className="interact-static inline-flex h-10 w-10 items-center justify-center rounded-full text-[#162A2C] transition-colors hover:bg-slate-100 lg:hidden"
              aria-expanded={mobileOpen}
              aria-controls="mobile-nav"
              aria-label={mobileOpen ? "Close menu" : "Open menu"}
              onClick={toggleMobile}
            >
              {mobileOpen ? (
                <X className="h-5 w-5" strokeWidth={2.25} />
              ) : (
                <Menu className="h-5 w-5" strokeWidth={2.25} />
              )}
            </button>
          </div>

          <div
            id="mobile-nav"
            className={cn(
              "grid transition-[grid-template-rows] duration-300 ease-out lg:hidden",
              mobileOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
            )}
            aria-hidden={!mobileOpen}
          >
            <div className="overflow-hidden">
              <div className="border-t border-slate-100 bg-white">
                <nav
                  className="flex flex-col gap-1 px-4 py-4 md:px-6 lg:px-8"
                  aria-label="Mobile"
                >
                  {navLinks.map((link) => (
                    <Link
                      key={link.href}
                      href={link.href}
                      className="interact-link rounded-xl px-3 py-3 text-[15px] font-medium text-slate-700 hover:bg-slate-50"
                      onClick={handleNavClick(link.href)}
                      tabIndex={mobileOpen ? 0 : -1}
                    >
                      {link.label}
                    </Link>
                  ))}
                  <Button
                    href={GITHUB_REPO}
                    external
                    className="mt-2 w-full"
                    onClick={closeMobile}
                  >
                    Github Repository
                  </Button>
                </nav>
              </div>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
