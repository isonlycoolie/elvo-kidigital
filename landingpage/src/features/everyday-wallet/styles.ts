export const everydayWalletStyles = {
  section:
    "scroll-mt-[var(--header-offset)] w-full bg-white pt-10 md:pt-12 lg:pt-14 pb-8 md:pb-10 lg:pb-12",
  sectionTitle:
    "text-[1.875rem] font-medium leading-[1.1] tracking-normal text-[#162A2C] sm:text-[2.25rem] md:text-5xl lg:text-[3.25rem]",
  sectionDescription:
    "mx-auto mt-4 max-w-[34rem] text-[15px] leading-[1.6] text-slate-600/90 md:text-[16px]",
  card:
    "mx-auto flex w-full max-w-[min(23rem,100%)] flex-col rounded-[1.5rem] bg-[#F3F3F3] p-5 md:p-6 lg:mx-0 lg:max-w-none",
  cardColumn:
    "flex w-full min-w-0 flex-col gap-3.5 md:gap-4 lg:max-w-none",
  cardColumnLeft: "order-2 lg:order-1",
  cardColumnRight: "order-3",
  cardTitle:
    "text-[0.9375rem] font-semibold leading-snug text-[#162A2C] md:text-[1rem]",
  cardDescription:
    "mt-2 text-[12px] leading-[1.6] text-slate-600/90 md:text-[13px]",
  visualWrap: "relative mb-3 flex w-full items-center justify-center",
  visualImage: "object-contain object-center",
  visualSendReceive: "h-[7rem] w-full sm:h-[8rem] lg:h-[8.75rem]",
  visualBills: "h-[8.25rem] w-full sm:h-[9.5rem] lg:h-[10.25rem]",
  visualMultiIcons:
    "relative mt-3 h-[9rem] w-full sm:h-[10.5rem] lg:h-[11.5rem]",
  phoneFrame:
    "relative h-[25rem] w-[13rem] sm:h-[29rem] sm:w-[15rem] md:h-[32rem] md:w-[17rem] lg:h-[41rem] lg:w-[21rem] xl:h-[43rem] xl:w-[22rem]",
  phoneColumn:
    "order-1 flex shrink-0 justify-center self-center lg:order-2",
  layoutGrid:
    "grid grid-cols-1 items-center gap-3.5 md:gap-4 lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] lg:gap-5 xl:gap-6",
} as const;
