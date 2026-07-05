import { Timer } from "lucide-react";
import {
  featureBanner,
  featureBannerColors,
  FeatureBannerContent,
  FeatureBannerPhone,
} from "@/shared/feature-banner";

const upcomingFeatures = [
  "Agent Cash-Out",
  "Family & Shared Accounts",
  "Delegated Access",
];

const colors = featureBannerColors.red;

export function ComingSoon() {
  return (
    <section id="waitlist" className={`scroll-mt-[var(--header-offset)] ${featureBanner.section}`}>
      <div className={featureBanner.container}>
        <div
          className={`relative overflow-hidden ${featureBanner.cardRadius} ${colors.card}`}
        >
          <div className={featureBanner.cardRow}>
            <FeatureBannerContent
              chip={
                <div className={`${featureBanner.chip} ${colors.chip}`}>
                  <Timer
                    className={`${featureBanner.chipIcon} ${colors.chipIcon}`}
                    strokeWidth={2.5}
                  />
                  <span className={`${featureBanner.chipText} ${colors.chip}`}>
                    Coming Soon
                  </span>
                </div>
              }
              title="Built to grow with you."
              description={
                <>
                  <span className="block lg:whitespace-nowrap">
                    ELVO&apos;s wallet is the foundation. Agent cash-out, family
                  </span>
                  <span className="block lg:whitespace-nowrap">
                    accounts, and delegated access are next, built on the same
                  </span>
                  <span className="block lg:whitespace-nowrap">
                    account rules and security you already trust.
                  </span>
                </>
              }
              features={upcomingFeatures}
              ctaLabel="Join the waitlist"
              ctaHref="#waitlist"
              colors={colors}
            />

            <FeatureBannerPhone
              src="/images/coming-soon/phone-mockup-coming-soon.svg"
              alt="ELVO app Coming Soon screen preview"
              position={colors.phonePosition}
              fade={colors.phoneFade}
              priority
            />
          </div>
        </div>
      </div>
    </section>
  );
}
