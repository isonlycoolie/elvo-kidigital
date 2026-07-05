"use client";

import { Minus, Plus } from "lucide-react";
import { useState } from "react";
import { siteCopy } from "@/content/site-copy";
import { cn } from "@/lib/utils";
import { trustedAdvantageStyles as ta } from "@/features/trusted-advantage/styles";

export function FaqAccordion() {
  const [openIndex, setOpenIndex] = useState<number | null>(null);
  const { items: faqItems } = siteCopy.faq;

  return (
    <div className="flex flex-col gap-3.5 md:gap-4">
      {faqItems.map((item, index) => {
        const isOpen = openIndex === index;
        const number = String(index + 1).padStart(2, "0");

        return (
          <div
            key={item.question}
            className="rounded-xl bg-[#F3F3F3] px-4 py-4 transition-colors hover:bg-[#ECECEC] md:px-5 md:py-[1.125rem]"
          >
            <button
              type="button"
              className="interact-static flex min-h-[2.5rem] w-full items-center gap-3 text-left md:min-h-[2.625rem]"
              aria-expanded={isOpen}
              onClick={() => setOpenIndex(isOpen ? null : index)}
            >
              <span className="shrink-0 text-[12px] text-slate-400 md:text-[13px]">
                {number}
              </span>
              <span className="min-w-0 flex-1 text-[1rem] font-medium leading-snug text-[#162A2C] md:text-[1.0625rem]">
                {item.question}
              </span>
              <span
                className={cn(
                  "flex h-7 w-7 shrink-0 items-center justify-center text-[#162A2C] transition-transform duration-300 ease-in-out",
                  isOpen && "rotate-180"
                )}
              >
                {isOpen ? (
                  <Minus className="h-5 w-5" strokeWidth={2.25} />
                ) : (
                  <Plus className="h-5 w-5" strokeWidth={2.25} />
                )}
              </span>
            </button>
            <div
              className={cn(
                "grid transition-[grid-template-rows] duration-300 ease-in-out",
                isOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
              )}
            >
              <div className="overflow-hidden">
                <p
                  className={cn(
                    `${ta.cardDescription} ml-7 pt-2 transition-[opacity,transform] duration-300 ease-in-out md:ml-8`,
                    isOpen
                      ? "translate-y-0 opacity-100"
                      : "-translate-y-1 opacity-0"
                  )}
                >
                  {item.answer}
                </p>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
