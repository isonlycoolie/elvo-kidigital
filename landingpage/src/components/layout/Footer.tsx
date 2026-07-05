import { ArrowUpRight, Mail } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui";
import { siteCopy } from "@/content/site-copy";
import { featureBanner } from "@/shared/feature-banner/styles";
import { mailtoContact } from "@/lib/links";

const productLinks = [
  { label: "Features", href: "#features" },
  { label: "Cards", href: "#cards" },
  { label: "Transfer", href: "#transfer" },
  { label: "Bills & Payments", href: "#bills" },
];

const companyLinks = [
  { label: "Trusted Advantage", href: "#trusted-advantage" },
  { label: "Everyday Wallet", href: "#everyday-wallet" },
  { label: "Contribute", href: "#contribution" },
];

const supportLinks = [
  { label: "FAQs", href: "#faq" },
  { label: "Contact", href: mailtoContact },
];

function FooterColumn({
  title,
  links,
}: {
  title: string;
  links: { label: string; href: string }[];
}) {
  return (
    <div>
      <p className="text-[12px] font-medium uppercase tracking-[0.12em] text-white/45">
        {title}
      </p>
      <ul className="mt-4 flex flex-col gap-2.5">
        {links.map((link) => (
          <li key={link.href + link.label}>
            <Link
              href={link.href}
              className="inline-flex items-center gap-1 text-[14px] font-medium text-white/75 transition-colors duration-200 hover:text-white"
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function Footer() {
  const { footer } = siteCopy;

  return (
    <footer className="w-full shrink-0 bg-white pt-5 pb-0 md:pt-6 lg:pt-8">
      <div className={`${featureBanner.container} max-w-[100rem] pb-0`}>
        <div className="relative overflow-hidden rounded-t-[2.25rem] rounded-b-none bg-[#0A0A0A] text-white md:rounded-t-[2.75rem] lg:rounded-t-[3rem]">
          <div className="px-6 py-10 sm:px-10 sm:py-12 md:px-12 md:py-12 lg:px-14 lg:py-14 xl:px-16">
            <div className="border-b border-white/[0.08] pb-12 md:pb-14 lg:pb-16">
              <div className="grid gap-12 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1.4fr)] lg:gap-16 xl:gap-20">
                <div className="max-w-md">
                  <Link
                    href="/"
                    className="inline-block text-[1.375rem] font-medium tracking-tight text-white md:text-[1.5rem]"
                  >
                    <span className="text-[#CC1E1E]">elvo</span>
                    digital
                  </Link>
                  <p className="mt-4 text-[15px] leading-[1.65] text-white/55 md:text-[16px]">
                    {footer.tagline}
                  </p>
                  <Button href={mailtoContact} className="mt-8">
                    {footer.cta}
                    <ArrowUpRight className="h-4 w-4" strokeWidth={2.25} />
                  </Button>
                </div>

                <div className="grid grid-cols-2 gap-8 sm:grid-cols-3 sm:gap-10">
                  <FooterColumn title="Product" links={productLinks} />
                  <FooterColumn title="Company" links={companyLinks} />
                  <FooterColumn title="Support" links={supportLinks} />
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-6 pt-7 md:flex-row md:items-center md:justify-between md:pt-8">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
                <p className="text-[13px] text-white/40">
                  © {new Date().getFullYear()} {footer.copyright}
                </p>
                <span className="hidden h-1 w-1 rounded-full bg-white/20 sm:block" />
                <div className="flex items-center gap-4">
                  <Link
                    href="#"
                    className="text-[13px] font-medium text-white/45 transition-colors hover:text-white/75"
                  >
                    Privacy
                  </Link>
                  <Link
                    href="#"
                    className="text-[13px] font-medium text-white/45 transition-colors hover:text-white/75"
                  >
                    Terms
                  </Link>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <a
                  href={mailtoContact}
                  className="interact-press inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-white/70 hover:border-white/20 hover:bg-white/[0.08] hover:text-white"
                  aria-label="Email ELVO Digital"
                >
                  <Mail className="h-4 w-4" strokeWidth={2} />
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </footer>
  );
}
