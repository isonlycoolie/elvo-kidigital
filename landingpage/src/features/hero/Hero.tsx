import Image from "next/image";
import { ChevronRight, Star } from "lucide-react";
import { HeroEntrance } from "@/components/motion";
import { Button } from "@/components/ui";
import { siteCopy } from "@/content/site-copy";
import { GITHUB_REPO } from "@/lib/links";
import avatar1 from "@/assets/avatar1.jpg";
import avatar2 from "@/assets/avatar2.jpg";

const heroAvatars = [
  { src: avatar2, alt: "ELVO user portrait" },
  { src: avatar1, alt: "ELVO user portrait" },
  { src: "/images/founder-quote/avatar.png", alt: "ELVO user portrait" },
] as const;

export function Hero() {
  const { hero } = siteCopy;

  return (
    <section id="hero" className="relative w-full overflow-hidden bg-[#FAFAFA] pt-24 md:pt-28 lg:pt-36 pb-0">
      <div className="container mx-auto px-4 md:px-6 lg:px-8 max-w-7xl">
        <div className="flex flex-col lg:flex-row items-center lg:items-start justify-between gap-4 sm:gap-6 lg:gap-8">
          
          {/* Left Content Column */}
          <HeroEntrance className="flex flex-col items-center space-y-4 text-center max-w-xl sm:space-y-6 lg:max-w-2xl lg:mt-10 lg:w-[55%] lg:items-start lg:space-y-6 lg:text-left">
            <div className="flex w-full flex-col items-center space-y-4 sm:space-y-6 lg:items-start lg:space-y-2">
            {/* Trust badge */}
            <div className="inline-flex items-center gap-1.5 rounded-full border border-slate-300/90 bg-transparent px-2.5 py-1">
              <div
                className="flex items-center gap-px"
                aria-label={`${hero.badgeStarCount} star trust badge`}
              >
                {Array.from({ length: hero.badgeStarCount }).map((_, index) => (
                  <Star
                    key={index}
                    className="h-2.5 w-2.5 fill-[#182321] text-[#182321]"
                  />
                ))}
              </div>
              <span className="text-[11px] font-medium leading-none text-slate-600 md:text-[12px]">
                {hero.badgeLabel}
              </span>
            </div>

            {/* Main Headline */}
            <h1 className="text-[2.25rem] font-medium leading-[0.84] tracking-[-0.02em] text-[#162A2C] md:text-5xl md:leading-[0.82] lg:text-[3.25rem] lg:leading-[0.6]">
              {hero.headlineLines[0]}<br className="hidden md:block" /> {hero.headlineLines[1]}
            </h1>

            {/* Subheadline */}
            <p className="mx-auto max-w-[95%] text-[17px] leading-[1.55] text-slate-600/90 md:text-lg lg:mx-0 lg:leading-[1.45]">
              {hero.subheadlineLines[0]}<br className="hidden md:block" />{" "}
              {hero.subheadlineLines[1]}<br className="hidden md:block" />{" "}
              {hero.subheadlineLines[2]}
            </p>
