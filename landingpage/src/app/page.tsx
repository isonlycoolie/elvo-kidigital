import { Hero, EverydayWallet, Features, Cards, TrustedAdvantage, Transfer, BillsPayments, ComingSoon, FounderQuote, SecuredVc, Faq, Contribution } from "@/features";

export default function Home() {
  return (
    <main className="flex min-h-screen w-full flex-1 flex-col">
      <Hero />
      <EverydayWallet />
      <Features />
      <Cards />
      <Transfer />
      <BillsPayments />
      <ComingSoon />
      <TrustedAdvantage />
      <FounderQuote />
      <SecuredVc />
      <Faq />
      <Contribution />
    </main>
  );
}
