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
