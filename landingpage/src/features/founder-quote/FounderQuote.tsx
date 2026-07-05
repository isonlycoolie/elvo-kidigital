import { Quote } from "lucide-react";
import Image from "next/image";
import { Reveal } from "@/components/motion";
import { siteCopy } from "@/content/site-copy";
import { featureBanner } from "@/shared/feature-banner/styles";
import { everydayWalletStyles as ew } from "@/features/everyday-wallet/styles";

export function FounderQuote() {
  const { founderQuote } = siteCopy;

  return (
    <section id="founder-quote" className={`relative overflow-hidden ${ew.section}`}>
      <div
        className="pointer-events-none absolute bottom-0 left-1/2 aspect-square w-[min(92vw,32rem)] -translate-x-1/2 translate-y-[80%] rounded-full bg-[radial-gradient(circle,rgba(204,30,30,0.16)_0%,rgba(204,30,30,0.09)_38%,rgba(204,30,30,0.03)_58%,transparent_70%)] sm:w-[38rem] md:w-[44rem] lg:w-[48rem]"
        aria-hidden
      />

      <div className={`relative z-10 ${featureBanner.container}`}>
        <div className="relative mx-auto flex w-full max-w-[48rem] flex-col items-center text-center lg:max-w-[56rem]">
          <Quote
            className="h-8 w-8 text-[#CC1E1E] md:h-9 md:w-9"
            strokeWidth={2.25}
            aria-hidden
          />

          <Reveal>
            <blockquote className="mt-6 w-full max-w-[46rem] text-[1.25rem] font-medium italic leading-[1.45] tracking-normal text-[#162A2C] sm:text-[1.375rem] md:mt-8 md:text-[1.5rem] lg:max-w-[54rem] lg:text-[1.625rem]">
              {founderQuote.quote}
            </blockquote>
          </Reveal>

          <Reveal delay={0.08}>
            <figcaption className="mt-8 flex flex-col items-center gap-3 md:mt-10">
              <div className="relative h-12 w-12 overflow-hidden rounded-full md:h-14 md:w-14">
                <Image
                  src="/images/founder-quote/avatar.png"
                  alt={founderQuote.name}
                  fill
                  className="object-cover"
                  sizes="56px"
                />
              </div>
              <div className="flex flex-col items-center gap-0.5">
                <p className="text-[1rem] font-semibold leading-snug text-[#162A2C] md:text-[1.0625rem]">
                  {founderQuote.name}
                </p>
                <p className="text-[12px] leading-[1.6] text-slate-600/90 md:text-[13px]">
                  {founderQuote.role}
                </p>
              </div>
            </figcaption>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
