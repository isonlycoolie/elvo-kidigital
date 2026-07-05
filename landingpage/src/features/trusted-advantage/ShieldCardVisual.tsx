import { ShieldCardPicker } from "./ShieldCardPicker";

export function ShieldCardVisual() {
  return (
    <div className="relative h-full w-full min-h-[11rem] lg:min-h-0">
      <div className="relative z-10 mx-auto mt-5 w-fit max-lg:mx-auto lg:absolute lg:right-24 lg:top-6 lg:mt-0">
        <ShieldCardPicker />
      </div>
    </div>
  );
}
