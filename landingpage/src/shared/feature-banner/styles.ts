export const featureBanner = {
  sectionAnchor: "scroll-mt-[var(--header-offset)]",
  section:
    "w-full bg-white pt-5 md:pt-6 lg:pt-8 pb-6 md:pb-8 lg:pb-10",
  container: "container mx-auto px-4 md:px-6 lg:px-8 max-w-7xl",
  cardRadius: "rounded-[2.25rem] md:rounded-[2.75rem] lg:rounded-[3rem]",
  cardRow: "flex flex-col lg:min-h-[36rem] lg:flex-row xl:min-h-[40rem]",
  contentColumn:
    "flex flex-col justify-between px-6 py-8 sm:px-10 sm:py-12 md:px-12 lg:min-h-[36rem] lg:w-[46%] lg:px-14 lg:py-16 xl:min-h-[40rem] xl:px-16",
  chip: "inline-flex items-center gap-1.5 rounded-full px-3 py-1.5",
  chipIcon: "h-[0.875rem] w-[0.875rem]",
  chipText: "text-[12px] font-bold leading-none tracking-tight md:text-[13px]",
  title:
    "mt-4 text-[1.625rem] font-medium leading-[1.1] tracking-normal sm:text-[2.25rem] lg:mt-5 lg:text-[2.625rem] xl:text-[2.875rem]",
  description:
    "mt-3 text-[14px] font-normal leading-[1.6] md:text-[15px] lg:mt-4",
  bulletList: "mt-4 flex flex-col gap-3 lg:mt-5",
  bulletItem: "flex items-center gap-3",
  bulletIcon:
    "flex h-[1.375rem] w-[1.375rem] shrink-0 items-center justify-center rounded-full",
  bulletCheck: "h-[0.625rem] w-[0.625rem]",
  bulletText: "text-[13px] font-normal md:text-[14px]",
  cta: "mt-10 inline-flex items-center gap-1.5 text-[14px] font-semibold transition-opacity hover:opacity-80 lg:mt-auto lg:pt-12 lg:text-[15px]",
  ctaArrow: "h-[0.875rem] w-[0.875rem]",
  phoneColumn:
    "relative mt-6 flex w-full items-end justify-center sm:mt-8 lg:mt-0 lg:w-[54%]",
  phoneFrame:
    "relative h-[17rem] w-full sm:h-[21.5rem] md:h-[24rem] lg:h-[32rem] xl:h-[36rem]",
} as const;

export const featureBannerColors = {
  red: {
    card: "bg-[#CC1E1E]",
    chip: "bg-white text-[#CC1E1E]",
    chipIcon: "text-[#CC1E1E]",
    title: "text-white",
    description: "text-white",
    bulletIcon: "bg-white",
    bulletCheck: "text-[#CC1E1E]",
    bulletText: "text-white",
    cta: "text-white",
    phoneFade: "from-[#CC1E1E] via-[#CC1E1E]/80",
    phonePosition: "lg:right-10 xl:right-14",
  },
  light: {
    card: "border border-[#EBEBEB] bg-white",
    chip: "bg-[#CC1E1E] text-white",
    chipIcon: "text-white",
    title: "text-[#162A2C]",
    description: "text-slate-600/90",
    bulletIcon: "bg-[#CC1E1E]",
    bulletCheck: "text-white",
    bulletText: "text-[#162A2C]",
    cta: "text-[#162A2C]",
    phoneFade: "from-white via-white/80",
    phonePosition: "lg:left-10 xl:left-14",
  },
  muted: {
    card: "bg-[#F9F9F9]",
    chip: "bg-[#CC1E1E] text-white",
    chipIcon: "text-white",
    title: "text-[#162A2C]",
    description: "text-slate-600/90",
    bulletIcon: "bg-[#CC1E1E]",
    bulletCheck: "text-white",
    bulletText: "text-[#162A2C]",
    cta: "text-[#162A2C]",
    phoneFade: "from-[#F9F9F9] via-[#F9F9F9]/80",
    phonePosition: "lg:right-10 xl:right-14",
  },
} as const;
