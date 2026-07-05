import { HelpCircle } from "lucide-react";
import Image from "next/image";
import { Reveal } from "@/components/motion";
import { Button } from "@/components/ui";
import { siteCopy } from "@/content/site-copy";
import { BentoChip } from "@/components/ui";
import { featureBanner } from "@/shared/feature-banner/styles";
import { trustedAdvantageStyles as ta } from "@/features/trusted-advantage/styles";
import { everydayWalletStyles as ew } from "@/features/everyday-wallet/styles";
import { FaqAccordion } from "./FaqAccordion";
import { mailtoContact } from "@/lib/links";

export function Faq() {
  const { faq } = siteCopy;

  return (
    <section id="faq" className={`scroll-mt-[var(--header-offset)] ${ew.section}`}>
      <div className={featureBanner.container}>
        <Reveal>
          <header className="mb-8 flex flex-col gap-5 lg:mb-10 lg:flex-row lg:items-start lg:justify-between lg:gap-16">
            <div>
              <p className={`${featureBanner.chipText} font-bold text-[#CC1E1E]`}>
                {faq.chip}
              </p>
              <h2 className={`mt-3 ${ta.sectionTitle}`}>
                {faq.headline}{" "}
                <span className="text-[#CC1E1E]">{faq.headlineAccent}</span>
              </h2>
            </div>
            <p className={`${ta.sectionDescription} lg:max-w-[26rem] lg:pt-2`}>
              {faq.description}
            </p>
          </header>
        </Reveal>

        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,26rem)_1fr] lg:gap-6 xl:grid-cols-[minmax(0,30rem)_1fr] xl:gap-8">
          <div className="order-1 w-full lg:order-2">
            <FaqAccordion />
          </div>

          <Reveal className="order-2 w-full lg:order-1">
            <article className="interact-lift flex w-full flex-col items-center rounded-[1.5rem] bg-[#F3F3F3] p-5 text-center md:p-7">
              <div className="relative mb-4 h-[6.5rem] w-full sm:h-[7.5rem]">
                <Image
                  src="/images/faq/faq-icons.svg"
                  alt="Support icons"
                  fill
                  className="object-contain object-center"
                  sizes="(max-width: 1024px) 90vw, 30rem"
                />
              </div>
              <BentoChip icon={HelpCircle} label={faq.support.chip} />
              <h3 className={`${ta.cardTitle}`}>
                {faq.support.title}
              </h3>
              <p className={ta.cardDescription}>
                {faq.support.description}
              </p>
              <Button href={mailtoContact} size="lg" className="mt-5">
                {faq.support.cta}
              </Button>
            </article>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
