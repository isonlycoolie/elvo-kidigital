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
                    unoptimized
                    className="svg-crisp card-asset-hover origin-center object-contain object-center lg:origin-right lg:object-right"
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
              <p className="mt-2 text-[12px] leading-[1.6] text-slate-600/90 md:text-[13px]">
                {ta.balance.description}
              </p>
            </div>
          </RevealItem>

          {/* Shield Card, wide */}
          <RevealItem as="article" className="group interact-lift relative z-10 flex flex-col overflow-hidden rounded-[1.5rem] bg-[#F3F3F3] lg:col-span-2 lg:min-h-[16rem] lg:flex-row lg:overflow-visible">
            <div className={`flex flex-1 flex-col lg:justify-between ${s.cardPadding} pb-3 lg:pb-7`}>
              <div>
                <BentoChip icon={CreditCard} label={ta.shieldCard.chip} variant="dark" />
                <h3 className="mt-4 text-[0.9375rem] font-semibold leading-snug text-[#162A2C] md:text-[1rem]">
                  {ta.shieldCard.title}
                </h3>
                <p className="mt-2 max-w-[22rem] text-[12px] leading-[1.6] text-slate-600/90 md:max-w-[24rem] md:text-[13px]">
                  {ta.shieldCard.description}
                </p>
              </div>
              <a
                href="#notify"
                className="interact-link mt-5 hidden items-center gap-1.5 text-[13px] font-semibold text-[#C29B42] md:text-[14px] lg:inline-flex"
              >
                {ta.shieldCard.cta}
                <ArrowRight className="h-3.5 w-3.5" strokeWidth={2.25} />
              </a>
            </div>

            {/* Mobile: card picker only */}
            <div className="flex justify-center px-4 pb-2 pt-0 lg:hidden">
              <ShieldCardPicker />
            </div>

            {/* Desktop: picker + bleed gold card */}
            <div className="relative hidden min-h-[16rem] flex-1 overflow-hidden lg:block lg:overflow-visible lg:pl-4 lg:pt-5">
              <ShieldCardVisual />
            </div>

            <div className="flex justify-end px-5 pb-5 md:px-6 lg:hidden">
              <a
                href="#notify"
                className={`${s.cardLink} interact-link text-[#C29B42]`}
              >
                {ta.shieldCard.cta}
                <ArrowRight className="h-3.5 w-3.5" strokeWidth={2.25} />
              </a>
            </div>

            <div className="pointer-events-none absolute bottom-0 right-0 z-30 hidden h-[16rem] w-[23.2rem] lg:-bottom-[3.25rem] lg:-right-16 lg:block">
              <Image
                src="/images/trusted-advantage/gold-card.svg"
                alt=""
                fill
                unoptimized
                className="svg-crisp card-asset-hover origin-bottom-right object-contain object-right object-bottom drop-shadow-[0_10px_24px_rgba(0,0,0,0.18)]"
                sizes="23.2rem"
                aria-hidden
              />
            </div>
          </RevealItem>
        </RevealGroup>

        {/* Our Promise */}
        <div className="mt-3.5 rounded-[1.5rem] bg-[#F3F3F3] p-5 md:mt-4 md:p-6 lg:p-8">
          <div className="mb-6 flex flex-col gap-5 lg:mb-8 lg:flex-row lg:items-start lg:justify-between lg:gap-16">
            <h3 className={s.promiseTitle}>
              {ta.promise.title}{" "}
              <span className="text-[#CC1E1E]">{ta.promise.titleAccent}</span>{" "}
              {ta.promise.titleEnd}
            </h3>
            <p className={s.promiseDescription}>
              {ta.promise.description}
            </p>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 md:grid-cols-3 md:gap-4">
            {ta.promiseStats.map((stat, index) => {
              const Icon = promiseStatIcons[index];
              return (
                <div
                  key={stat.label}
                  className="flex flex-col rounded-[1.25rem] bg-white px-5 py-6 md:px-6 md:py-7"
                >
                  <Icon
                    className="h-6 w-6 text-[#CC1E1E] md:h-7 md:w-7"
                    strokeWidth={2}
                  />
                  <p className={s.promiseStatValue}>{stat.value}</p>
                  <p className={s.promiseStatLabel}>{stat.label}</p>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
