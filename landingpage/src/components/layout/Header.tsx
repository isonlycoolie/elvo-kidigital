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
