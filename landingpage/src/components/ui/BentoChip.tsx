import { LucideIcon } from "lucide-react";
import { featureBanner } from "@/shared/feature-banner/styles";

type BentoChipProps = {
  icon: LucideIcon;
  label: string;
  variant?: "red" | "dark";
};

export function BentoChip({ icon: Icon, label, variant = "red" }: BentoChipProps) {
  const styles =
    variant === "red"
      ? "bg-[#CC1E1E] text-white"
      : "bg-[#162A2C] text-white";

  return (
    <div className={`${featureBanner.chip} ${styles}`}>
      <Icon className={`${featureBanner.chipIcon} text-white`} strokeWidth={2.5} />
      <span className={`${featureBanner.chipText} text-white`}>{label}</span>
    </div>
  );
}
