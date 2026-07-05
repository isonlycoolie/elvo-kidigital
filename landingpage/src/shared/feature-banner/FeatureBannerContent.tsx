import { ArrowRight, Check } from "lucide-react";
import type { ReactNode } from "react";
import { Reveal } from "@/components/motion";
import { featureBanner } from "./styles";

type FeatureBannerContentProps = {
  chip: ReactNode;
  title: string;
  description: ReactNode;
  features: string[];
  ctaLabel: string;
  ctaHref: string;
  colors: {
    title: string;
    description: string;
    bulletIcon: string;
    bulletCheck: string;
    bulletText: string;
    cta: string;
  };
};

export function FeatureBannerContent({
  chip,
  title,
  description,
  features,
  ctaLabel,
  ctaHref,
  colors,
}: FeatureBannerContentProps) {
  return (
    <div className={featureBanner.contentColumn}>
      <Reveal>
        <div>
          {chip}

          <h2 className={`${featureBanner.title} ${colors.title}`}>{title}</h2>

          <div className={`${featureBanner.description} ${colors.description}`}>
            {description}
          </div>

          <ul className={featureBanner.bulletList}>
            {features.map((feature) => (
              <li key={feature} className={featureBanner.bulletItem}>
                <span className={`${featureBanner.bulletIcon} ${colors.bulletIcon}`}>
                  <Check
                    className={`${featureBanner.bulletCheck} ${colors.bulletCheck}`}
                    strokeWidth={3}
                  />
                </span>
                <span className={`${featureBanner.bulletText} ${colors.bulletText}`}>
                  {feature}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </Reveal>

      <a
        href={ctaHref}
        className={`interact-press ${featureBanner.cta} ${colors.cta}`}
      >
        {ctaLabel}
        <ArrowRight className={featureBanner.ctaArrow} strokeWidth={2.25} />
      </a>
    </div>
  );
}
