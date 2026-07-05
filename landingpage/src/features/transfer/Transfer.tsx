import { Zap } from "lucide-react";
import { siteCopy } from "@/content/site-copy";
import {
  featureBanner,
  featureBannerColors,
  FeatureBannerContent,
  FeatureBannerPhone,
} from "@/shared/feature-banner";

const colors = featureBannerColors.light;
const { transfer } = siteCopy;

export function Transfer() {
  return (
    <section id="transfer" className={`scroll-mt-[var(--header-offset)] ${featureBanner.section}`}>
      <div className={featureBanner.container}>
        <div
          className={`relative overflow-hidden ${featureBanner.cardRadius} ${colors.card}`}
        >
          <div className={featureBanner.cardRow}>
            <FeatureBannerPhone
              src="/images/transfer/phone-mockup-transfer.svg"
              alt="ELVO app Transfer screen preview"
              position={colors.phonePosition}
              fade={colors.phoneFade}
            />

            <FeatureBannerContent
              chip={
                <div className={`${featureBanner.chip} ${colors.chip}`}>
                  <Zap
                    className={`${featureBanner.chipIcon} ${colors.chipIcon}`}
                    strokeWidth={2.5}
                  />
                  <span className={`${featureBanner.chipText} text-white`}>
                    Transfer
                  </span>
                </div>
              }
              title={transfer.title}
              description={<>{transfer.description}</>}
              features={[...transfer.features]}
              ctaLabel={transfer.ctaLabel}
              ctaHref="#transfer"
              colors={colors}
            />
          </div>
        </div>
      </div>
    </section>
  );
}
