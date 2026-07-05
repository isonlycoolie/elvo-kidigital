import Image from "next/image";
import { Wallet } from "lucide-react";
import { Reveal } from "@/components/motion";
import { siteCopy } from "@/content/site-copy";

export function Cards() {
  const { cards } = siteCopy;

  return (
    <section id="cards" className="scroll-mt-[var(--header-offset)] w-full bg-white pt-6 md:pt-8 lg:pt-10 pb-10 md:pb-16 lg:pb-24">
      <div className="container mx-auto px-4 md:px-6 lg:px-8 max-w-7xl">
        <div className="flex flex-col lg:flex-row items-center justify-between gap-8 lg:gap-12">
          
          {/* Left Column - Image */}
          <div className="group relative flex w-full justify-center lg:w-1/2 lg:justify-start">
            <div className="relative aspect-[4/3] w-full max-w-[500px] lg:max-w-[600px]">
              <Image 
                src="/images/cards/Stacking-cards.svg" 
                alt={cards.imageAlt}
                fill
                className="card-asset-hover origin-center object-contain"
                sizes="(max-width: 1024px) 100vw, 50vw"
              />
            </div>
          </div>

          {/* Right Column - Text Content */}
          <div className="w-full lg:w-1/2 flex flex-col items-start lg:max-w-[540px]">
            <Reveal>
              <h2 className="text-[1.875rem] sm:text-[2.25rem] md:text-5xl lg:text-[3.25rem] font-medium tracking-normal text-[#162A2C] leading-[1.1] mb-4">
                {cards.headline}{" "}
                <span className="text-[#CC1E1E]">{cards.headlineAccent}</span>
                <br className="hidden md:block" /> {cards.headlineEnd}
              </h2>
            </Reveal>
            
            <Reveal delay={0.08}>
              <p className="text-[17px] md:text-lg text-slate-600/90 leading-[1.6] mb-8">
                {cards.description}
              </p>

              {/* Card Features List */}
              <div className="flex flex-col space-y-5 md:space-y-6">
                {cards.types.map((card, index) => (
                  <div key={index} className="flex items-start space-x-4">
                    <div className={`mt-0.5 ${card.iconColor}`}>
                      <Wallet className="w-[22px] h-[22px] stroke-[2]" />
                    </div>
                    <div className="flex flex-col space-y-1">
                      <h4 className="text-[15px] md:text-base font-bold text-[#162A2C] tracking-tight">
                        {card.title}
                      </h4>
                      <p className="text-[14px] md:text-[15px] text-slate-500/90 leading-snug">
                        {card.description}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </Reveal>
          </div>

        </div>
      </div>
    </section>
  );
}
