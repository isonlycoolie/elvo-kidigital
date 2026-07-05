import { ArrowRight, Building2, CalendarDays, CreditCard, FileCheck, Send, Users } from "lucide-react";
import Image from "next/image";
import { Reveal, RevealGroup, RevealItem } from "@/components/motion";
import { siteCopy } from "@/content/site-copy";
import { BentoChip } from "@/components/ui";
import { BalanceMockup } from "./BalanceMockup";
import { ShieldCardPicker } from "./ShieldCardPicker";
import { ShieldCardVisual } from "./ShieldCardVisual";
import { trustedAdvantageStyles as s } from "./styles";

const promiseStatIcons = [Users, Building2, FileCheck] as const;
const { trustedAdvantage: ta } = siteCopy;

export function TrustedAdvantage() {
  return (
    <section id="trusted-advantage" className={`scroll-mt-[var(--header-offset)] ${s.section}`}>
      <div className="container mx-auto px-4 md:px-6 lg:px-8 max-w-7xl">
        {/* Section Header */}
        <Reveal>
        <div className="mb-6 flex flex-col justify-between gap-5 md:mb-8 lg:flex-row lg:items-start lg:gap-16">
          <h2 className={s.sectionTitle}>
            {ta.sectionTitle}{" "}
            <span className="text-[#CC1E1E]">{ta.sectionTitleAccent}</span>{" "}
            {ta.sectionTitleEnd}
          </h2>
          <p className={s.sectionDescription}>
            {ta.sectionDescription}
          </p>
        </div>
        </Reveal>

        {/* Bento Grid */}
        <RevealGroup className="grid grid-cols-1 gap-3.5 md:gap-4 lg:grid-cols-3">
          {/* Elvo Card, wide */}
          <RevealItem as="article" className="group interact-lift flex flex-col overflow-hidden rounded-[1.5rem] bg-[#F3F3F3] lg:col-span-2 lg:min-h-[16rem] lg:flex-row">
            <div className={`flex flex-1 flex-col lg:justify-between ${s.cardPadding} pb-3 lg:pb-7`}>
              <div>
                <BentoChip icon={CreditCard} label={ta.elvoCard.chip} />
                <h3 className="mt-4 text-[0.9375rem] font-semibold leading-snug text-[#162A2C] md:text-[1rem]">
                  {ta.elvoCard.title}
                </h3>
                <p className="mt-2 max-w-[22rem] text-[12px] leading-[1.6] text-slate-600/90 md:max-w-[24rem] md:text-[13px]">
                  {ta.elvoCard.description}
                </p>
              </div>
              <a
                href="#waitlist"
                className="interact-link mt-5 hidden items-center gap-1.5 text-[12px] font-semibold text-[#162A2C] md:text-[13px] lg:inline-flex"
              >
                {ta.elvoCard.cta}
                <ArrowRight className="h-3 w-3" strokeWidth={2.25} />
              </a>
            </div>
            <div className="relative flex min-h-[12rem] flex-1 items-center justify-center lg:min-h-0 lg:items-stretch">
              <div className="relative mx-auto h-[13rem] w-full max-w-[18rem] translate-y-3 sm:h-[15rem] sm:max-w-[20rem] sm:translate-y-4 lg:absolute lg:inset-0 lg:mx-0 lg:flex lg:max-w-none lg:translate-y-5 lg:items-center lg:justify-end xl:translate-y-6">
                <div className="relative h-full w-full lg:h-[23rem] lg:w-[38rem] lg:max-w-none">
                  <Image
                    src="/images/trusted-advantage/stacked-cards.svg"
                    alt="ELVO stacked cards: Standard, Team, and Shield"
                    fill
                    className="card-asset-hover origin-center object-contain object-center lg:origin-right lg:object-right"
                    sizes="(max-width: 1024px) 70vw, 38rem"
                  />
                </div>
              </div>
            </div>
            <div className="flex justify-end px-5 pb-5 md:px-6 lg:hidden">
              <a
                href="#waitlist"
                className={`${s.cardLink} interact-link text-[#162A2C]`}
              >
                {ta.elvoCard.cta}
                <ArrowRight className="h-3 w-3" strokeWidth={2.25} />
              </a>
            </div>
          </RevealItem>

          {/* Send Money, narrow */}
          <RevealItem as="article" className={`interact-lift flex flex-col justify-between rounded-[1.5rem] bg-[#F3F3F3] ${s.cardPadding} lg:min-h-[16rem]`}>
            <div>
              <BentoChip icon={Send} label={ta.sendMoney.chip} />
              <h3 className={s.cardTitle}>{ta.sendMoney.title}</h3>
              <p className={s.cardDescription}>
                {ta.sendMoney.description}
              </p>
            </div>
          </RevealItem>

          {/* Balance, narrow */}
          <RevealItem as="article" className={`interact-lift flex flex-col rounded-[1.5rem] bg-[#F3F3F3] ${s.cardPadding} lg:min-h-[16rem]`}>
            <BalanceMockup />
            <div className="mt-6">
              <BentoChip icon={CalendarDays} label={ta.balance.chip} />
              <h3 className="mt-4 text-[0.9375rem] font-semibold leading-snug text-[#162A2C] md:text-[1rem]">
                {ta.balance.title}
              </h3>
