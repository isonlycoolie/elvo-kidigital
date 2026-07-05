import { GitFork, Star } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { Reveal } from "@/components/motion";
import { Button } from "@/components/ui";
import {
  featureBanner,
  featureBannerColors,
  FeatureBannerPhone,
} from "@/shared/feature-banner";
import { GITHUB_REPO, GITHUB_REPO_FORK } from "@/lib/links";

function GithubIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="currentColor"
      className={className}
      aria-hidden
    >
      <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0 1 12 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0 0 22 12.017C22 6.484 17.522 2 12 2Z" />
    </svg>
  );
}

const colors = featureBannerColors.red;

export function Contribution() {
  return (
    <section id="contribution" className={`scroll-mt-[var(--header-offset)] ${featureBanner.section}`}>
      <div className={`${featureBanner.container} max-w-[88rem]`}>
        <div
          className={`relative overflow-hidden ${featureBanner.cardRadius} ${colors.card}`}
        >
          <div className={featureBanner.cardRow}>
            <div
              className={`${featureBanner.contentColumn} lg:w-[50%] xl:w-[52%]`}
            >
              <Reveal>
              <div className="w-full lg:max-w-[38rem] xl:max-w-[44rem]">
                <Link
                  href={GITHUB_REPO}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={`interact-press inline-flex items-center gap-2 ${featureBanner.cta} ${colors.cta} lg:pt-0`}
                >
                  <GithubIcon className="h-4 w-4" />
                  Go to Github Repository
                </Link>

                <h2 className={`${featureBanner.title} ${colors.title}`}>
                  Dive in and help build
                  <br className="hidden lg:block" />
                  the future of ELVO.
                </h2>

                <p className={`${featureBanner.description} ${colors.description}`}>
                  Browse the source, open an issue, and take part in building the
                  wallet. Fork, contribute, and ship with the community.
                </p>

                <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                  <Button href={GITHUB_REPO} external variant="dark">
                    <Star className="h-4 w-4" strokeWidth={2.25} />
                    Star on Github
                  </Button>
                  <Button href={GITHUB_REPO_FORK} external variant="dark">
                    <GitFork className="h-4 w-4" strokeWidth={2.25} />
                    Fork the Repo
                  </Button>
                </div>

                <p className={`mt-8 text-center lg:text-left ${featureBanner.description} ${colors.description}`}>
                  Or scan the QR code below
                </p>
                <div className="mx-auto mt-3 w-fit lg:mx-0">
                  <div className="overflow-hidden rounded-xl bg-white/95 p-3">
                    <div className="relative h-[7rem] w-[7rem]">
                      <Image
                        src="/images/contribution/qr-code.svg"
                        alt="QR code to ELVO Github repository"
                        fill
                        className="object-contain"
                        sizes="112px"
                      />
                    </div>
                  </div>
                </div>
              </div>
              </Reveal>
            </div>

            <FeatureBannerPhone
              src="/images/contribution/device-mockup.svg"
              alt="ELVO app overview on contribution section"
              position={colors.phonePosition}
              fade={colors.phoneFade}
              className="hidden lg:flex lg:w-[50%] xl:w-[48%]"
            />
          </div>
        </div>
      </div>
    </section>
  );
}
