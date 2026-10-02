"use client";

import { useTextos } from "@/lib/config/textos-provider";

/** Selo de funcionalidade em validação. Herda a cor do item (currentColor), então serve no menu claro e no escuro. */
export function SeloBeta() {
  const rotulo = useTextos().menu.beta;
  return (
    <span className="shrink-0 rounded-full border border-current px-1.5 py-px text-[9.5px] leading-none font-bold tracking-wide uppercase">
      {rotulo}
    </span>
  );
}
