import { Receipt } from "lucide-react";
import { siteCopy } from "@/content/site-copy";
import {
  featureBanner,
  featureBannerColors,
  FeatureBannerContent,
  FeatureBannerPhone,
} from "@/shared/feature-banner";

const colors = featureBannerColors.muted;
const { bills } = siteCopy;

export function BillsPayments() {
  return (
    <section id="bills" className={`scroll-mt-[var(--header-offset)] ${featureBanner.section}`}>
      <div className={featureBanner.container}>
        <div
          className={`relative overflow-hidden ${featureBanner.cardRadius} ${colors.card}`}
        >
          <div className={featureBanner.cardRow}>
            <FeatureBannerContent
              chip={
                <div className={`${featureBanner.chip} ${colors.chip}`}>
                  <Receipt
                    className={`${featureBanner.chipIcon} ${colors.chipIcon}`}
                    strokeWidth={2.5}
                  />
                  <span className={`${featureBanner.chipText} text-white`}>
                    Bills Payments
                  </span>
                </div>
              }
              title={bills.title}
              description={<>{bills.description}</>}
              features={[...bills.features]}
              ctaLabel={bills.ctaLabel}
              ctaHref="#bills"
              colors={colors}
            />

            <FeatureBannerPhone
              src="/images/bills/phone-mockup-bills.svg"
              alt="ELVO app Bills Payments screen preview"
              position={colors.phonePosition}
              fade={colors.phoneFade}
            />
          </div>
        </div>
      </div>
    </section>
  );
}
