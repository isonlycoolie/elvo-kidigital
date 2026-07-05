import { ChevronDown, Users, Wallet } from "lucide-react";

export function BalanceMockup() {
  return (
    <div className="w-full pt-2">
      <div className="mx-auto flex w-full max-w-[min(17rem,100%)] flex-col gap-2.5">
        <div className="flex justify-center">
          <div className="inline-flex items-center gap-1 rounded-full bg-[#CC1E1E]/18 px-3 py-1 text-[9px] font-medium leading-none text-[#CC1E1E] md:text-[10px]">
            Account balance
            <ChevronDown className="h-2.5 w-2.5 opacity-80" strokeWidth={2.5} />
          </div>
        </div>

        <div className="flex items-center justify-between gap-3 rounded-xl border border-[#E5E5E5] bg-white px-3 py-2.5">
          <p className="min-w-0 leading-none">
            <span className="text-[12px] font-semibold text-[#162A2C] md:text-[13px]">
              2,000,000
            </span>{" "}
            <span className="text-[10px] font-normal text-slate-400 md:text-[11px]">
              tzs
            </span>
          </p>
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#2D9B5F]">
            <Wallet className="h-3 w-3 text-white" strokeWidth={2.25} />
          </span>
        </div>

        <div className="flex items-center justify-between gap-3 rounded-xl border border-[#E5E5E5] bg-white px-3 py-2.5">
          <p className="min-w-0 leading-tight">
            <span className="text-[11px] font-semibold text-[#162A2C] md:text-[12px]">
              Team
            </span>{" "}
            <span className="text-[10px] font-normal text-slate-400 md:text-[11px]">
              = 117,333.80 tzs
            </span>
          </p>
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#CC1E1E]">
            <Users className="h-3 w-3 text-white" strokeWidth={2.25} />
          </span>
        </div>
      </div>
    </div>
  );
}
