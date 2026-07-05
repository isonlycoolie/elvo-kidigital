import { Ban, Globe } from "lucide-react";
import Image from "next/image";
import { Reveal, RevealGroup, RevealItem } from "@/components/motion";
import { siteCopy } from "@/content/site-copy";
import { BentoChip } from "@/components/ui";
import { everydayWalletStyles as s } from "./styles";

export function EverydayWallet() {
  const { everydayWallet: ew } = siteCopy;

  return (
    <section id="everyday-wallet" className={s.section}>
      <div className="container mx-auto max-w-7xl px-4 md:px-6 lg:px-8">
        <Reveal>
          <header className="mb-8 text-center md:mb-10">
            <h2 className={s.sectionTitle}>
              {ew.sectionTitle}{" "}
              <span className="text-[#CC1E1E]">{ew.sectionTitleAccent}</span>{" "}
              {ew.sectionTitleEnd}
            </h2>
            <p className={s.sectionDescription}>
              {ew.sectionDescription}
            </p>
          </header>
        </Reveal>

        <div className={s.layoutGrid}>
          {/* Left column */}
          <RevealGroup className={`${s.cardColumn} ${s.cardColumnLeft}`}>
            <RevealItem as="article" className={`interact-lift ${s.card}`}>
              <div className={`${s.visualWrap} ${s.visualSendReceive}`}>
                <Image
                  src="/images/everyday-wallet/send-receive.svg"
                  alt="Send to Coolie and receive from Jane"
                  fill
                  className={s.visualImage}
                  sizes="(max-width: 1024px) 90vw, 28rem"
                />
              </div>
              <h3 className={s.cardTitle}>{ew.sendReceive.title}</h3>
              <p className={s.cardDescription}>
                {ew.sendReceive.description}
              </p>
            </RevealItem>

            <RevealItem as="article" className={`interact-lift ${s.card}`}>
              <div className={`${s.visualWrap} ${s.visualBills}`}>
                <Image
                  src="/images/everyday-wallet/bills.svg"
                  alt="Electricity and water bill payments"
                  fill
                  className={s.visualImage}
                  sizes="(max-width: 1024px) 90vw, 28rem"
                />
              </div>
              <h3 className={s.cardTitle}>{ew.bills.title}</h3>
              <p className={s.cardDescription}>
                {ew.bills.description}
              </p>
            </RevealItem>
          </RevealGroup>

          {/* Center phone */}
          <div className={s.phoneColumn}>
            <div className={s.phoneFrame}>
              <Image
                src="/images/everyday-wallet/phone-mockup.svg"
                alt="ELVO app overview screen"
                fill
                className="object-contain"
                sizes="(max-width: 1024px) 58vw, 22rem"
                priority
              />
            </div>
          </div>

          {/* Right column */}
          <RevealGroup className={`${s.cardColumn} ${s.cardColumnRight}`}>
            <RevealItem as="article" className={`interact-lift ${s.card} items-center text-center`}>
              <BentoChip icon={Globe} label={ew.multiUse.chip} />
              <h3 className={`${s.cardTitle} mt-4`}>
                {ew.multiUse.title}
              </h3>
              <p className={s.cardDescription}>
                {ew.multiUse.description}
              </p>
              <div className={s.visualMultiIcons}>
                <Image
                  src="/images/everyday-wallet/multi-icons.svg"
                  alt="Wallet use cases for personal, family, education, and business"
                  fill
                  className={s.visualImage}
                  sizes="(max-width: 1024px) 90vw, 28rem"
                />
              </div>
            </RevealItem>

            <RevealItem as="article" className={`interact-lift ${s.card} items-center justify-between text-center lg:min-h-[14.5rem]`}>
              <BentoChip icon={Ban} label={ew.noSurprise.chip} />
              <h3 className={`${s.cardTitle} mt-4`}>
                {ew.noSurprise.title}
              </h3>
              <p className={s.cardDescription}>
                {ew.noSurprise.description}
              </p>
            </RevealItem>
          </RevealGroup>
        </div>
      </div>
    </section>
  );
}
