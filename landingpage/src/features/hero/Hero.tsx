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
            </div>

            {/* CTA Button */}
            <div className="flex justify-center pt-0 lg:justify-start lg:pt-2">
              <Button href={GITHUB_REPO} external size="lg">
                <span>{hero.cta}</span>
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>

            {/* Social Proof */}
            <div className="hidden items-center space-x-4 pt-8 sm:pt-12 md:flex md:pt-28">
              <div className="flex -space-x-2.5">
                {heroAvatars.map((avatar, index) => (
                  <div
                    key={avatar.alt + index}
                    className="relative h-9 w-9 overflow-hidden rounded-full border-2 border-[#FAFAFA] bg-slate-300 shadow-sm"
                    style={{ zIndex: heroAvatars.length - index }}
                  >
                    <Image
                      src={avatar.src}
                      alt={avatar.alt}
                      fill
                      className="object-cover"
                      sizes="36px"
                    />
                  </div>
                ))}
              </div>
              <span className="text-[15px] font-medium text-slate-700/90 tracking-tight">{hero.socialProof}</span>
            </div>
          </HeroEntrance>

          {/* Right Image Column */}
          <HeroEntrance
            delay={0.1}
            className="relative flex w-full justify-center lg:mt-0 lg:w-[45%] lg:justify-end"
          >
            <div className="relative h-[26rem] w-[15.5rem] max-w-[88vw] transform sm:h-[29.5rem] sm:w-[17.25rem] md:h-[29.5rem] md:w-[19.5rem] lg:h-[34rem] lg:w-[23.5rem] lg:-translate-x-8 lg:translate-y-4 xl:-translate-x-12">
              <Image 
                src="/images/hero/Home-mobile-mock.svg" 
                alt="Elvo App Interface" 
                fill
                priority
                className="object-contain object-top"
                sizes="(max-width: 768px) 100vw, 50vw"
              />
            </div>
          </HeroEntrance>

        </div>
      </div>
    </section>
  );
}
