import Image from "next/image";
import { cn } from "@/lib/utils";
import { featureBanner } from "./styles";

type FeatureBannerPhoneProps = {
  src: string;
  alt: string;
  position: string;
  fade: string;
  priority?: boolean;
  className?: string;
};

export function FeatureBannerPhone({
  src,
  alt,
  position,
  fade,
  priority = false,
  className,
}: FeatureBannerPhoneProps) {
  return (
    <div className={cn(featureBanner.phoneColumn, className)}>
      <div
        className={`relative w-[15rem] sm:w-[17.5rem] md:w-[19.5rem] lg:absolute lg:bottom-0 lg:w-[24.5rem] xl:w-[27rem] ${position}`}
      >
        <div className={featureBanner.phoneFrame}>
          <Image
            src={src}
            alt={alt}
            fill
            priority={priority}
            className="object-contain object-bottom"
            sizes="(max-width: 1024px) 55vw, 27rem"
          />
          <div
            className={`pointer-events-none absolute inset-x-0 bottom-0 h-[22%] bg-gradient-to-t to-transparent ${fade}`}
            aria-hidden
          />
        </div>
      </div>
    </div>
  );
}
