import Image from "next/image";
import { Reveal, RevealGroup, RevealItem } from "@/components/motion";
import { siteCopy } from "@/content/site-copy";

export function Features() {
  const { features } = siteCopy;

  return (
    <section id="features" className="scroll-mt-[var(--header-offset)] w-full bg-white pt-16 md:pt-24 lg:pt-28 pb-8 md:pb-10 lg:pb-12">
      <div className="container mx-auto px-4 md:px-6 lg:px-8 max-w-7xl">
        
        {/* Top Header */}
        <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-6 lg:gap-16 mb-8 md:mb-10">
          <Reveal>
            <h2 className="text-[2.25rem] md:text-5xl lg:text-[3.25rem] font-medium tracking-normal text-[#162A2C] leading-[1.1] lg:max-w-[600px]">
              {features.headline}{" "}
              <span className="text-[#CC1E1E]">{features.headlineAccent}</span>
            </h2>
          </Reveal>
          <div className="lg:w-[45%] pt-2 md:pt-4">
            <p className="text-[17px] md:text-lg text-slate-600/90 leading-[1.6]">
              {features.description}
            </p>
          </div>
        </div>

        {/* Feature Cards Grid */}
        <RevealGroup className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 md:gap-8">
          {features.cards.map((feature, idx) => (
            <RevealItem
              key={idx}
              className="interact-lift flex flex-col rounded-[24px] bg-[#F3F3F3] p-6 sm:p-8 md:bg-[#FAF9F9] md:p-10"
            >
              {/* Image Container */}
              <div className="flex-1 w-full flex items-center justify-center min-h-[120px] md:min-h-[150px] mb-4 relative">
                <div className="relative w-full h-[110px] md:h-[140px]">
                  <Image 
                    src={feature.image} 
                    alt={feature.alt} 
                    fill 
                    className="object-contain"
                  />
                </div>
              </div>

              {/* Text Content */}
              <div className="flex flex-col space-y-3 mt-auto">
                <h3 className="text-[20px] md:text-[22px] font-semibold text-[#162A2C] tracking-tight">
                  {feature.title}
                </h3>
                <p className="text-[15px] md:text-base text-slate-600/90 leading-[1.6]">
                  {feature.description}
                </p>
              </div>
            </RevealItem>
          ))}
        </RevealGroup>

      </div>
    </section>
  );
}
