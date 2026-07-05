import Image from "next/image";
import { Reveal } from "@/components/motion";
import { siteCopy } from "@/content/site-copy";
import { featureBanner } from "@/shared/feature-banner/styles";
import { trustedAdvantageStyles as ta } from "@/features/trusted-advantage/styles";
import { everydayWalletStyles as ew } from "@/features/everyday-wallet/styles";

export function SecuredVc() {
  const { securedVc } = siteCopy;

  return (
    <section id="secured-vc" className={ew.section}>
      <div className={featureBanner.container}>
        <div className="flex flex-col gap-10 lg:flex-row lg:items-center lg:justify-between lg:gap-12">
          <Reveal className="lg:w-[50%]">
            <p
              className={`${featureBanner.chipText} font-bold text-[#CC1E1E]`}
            >
              {securedVc.chip}
            </p>
            <h2 className={`mt-3 lg:max-w-[34rem] ${ta.sectionTitle}`}>
              <span className="text-[#CC1E1E]">{securedVc.headlineAccent}</span>{" "}
              {securedVc.headline}
            </h2>
            <p className={`mt-4 ${ta.sectionDescription}`}>
              {securedVc.description}
            </p>
          </Reveal>

          <div className="flex w-full justify-center lg:w-[45%] lg:justify-end">
            <div className="relative h-[14rem] w-full max-w-[28rem] sm:h-[16rem] md:h-[18rem] lg:h-[22rem] lg:max-w-none xl:h-[24rem]">
              <Image
                src="/images/secured-vc/red-card.svg"
                alt={securedVc.imageAlt}
                fill
                unoptimized
                className="svg-crisp object-contain object-center lg:object-right"
                sizes="(max-width: 1024px) 90vw, 28rem"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
