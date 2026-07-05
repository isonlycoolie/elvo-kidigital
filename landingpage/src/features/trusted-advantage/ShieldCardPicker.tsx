import { Check } from "lucide-react";

const cardOptions = [
  { label: "Elvo card", meta: "Free", selected: false },
  { label: "Elvo Team Card", meta: "Coming Soon", selected: true },
  { label: "Elvo Shield Card", meta: null, selected: false },
];

export function ShieldCardPicker() {
  return (
    <ul className="flex w-full max-w-[min(16.5rem,calc(100vw-3rem))] flex-col gap-1.5 overflow-visible md:max-w-[17rem]">
      {cardOptions.map((option, index) => (
        <li
          key={option.label}
          className={`flex w-full items-center justify-between rounded-xl border bg-white px-2.5 py-2 md:px-3 md:py-2.5 ${
            option.selected ? "border-[#C9A227]" : "border-[#E5E5E5]"
          } ${
            index === 1
              ? "ml-4 w-[calc(100%+0.75rem)] lg:ml-8 lg:w-[calc(100%+2rem)]"
              : ""
          }`}
        >
          <div className="flex min-w-0 items-center gap-2">
            <span
              className={`flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full border ${
                option.selected
                  ? "border-[#C9A227] bg-[#C9A227]"
                  : "border-[#C5C5C5] bg-white"
              }`}
            >
              {option.selected && (
                <Check className="h-2 w-2 text-white" strokeWidth={3.5} />
              )}
            </span>
            <span className="truncate text-[10px] font-medium text-[#162A2C] md:text-[11px]">
              {option.label}
            </span>
          </div>
          {option.meta && (
            <span
              className={`ml-1.5 shrink-0 text-[9px] font-medium md:text-[10px] ${
                option.selected ? "text-[#C9A227]" : "text-slate-400"
              }`}
            >
              {option.meta}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}
