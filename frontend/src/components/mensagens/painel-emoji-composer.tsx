"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import { Smile } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { Textos } from "@/lib/config/schema";

const SeletorEmojiCompleto = dynamic(
  () => import("./seletor-emoji-completo").then((modulo) => modulo.SeletorEmojiCompleto),
  { ssr: false },
);

type Props = {
  rotulo: string;
  i18n: Textos["atendimentos"]["mensagem"]["acoes"]["seletor"];
  disabled?: boolean;
  onEscolher: (emoji: string) => void;
};

/**
 * Catalogo Unicode completo no composer (busca, categorias, tom de pele).
 * O picker so monta com o popover aberto para nao pesar o caminho de envio.
 */
export function PainelEmojiComposer({ rotulo, i18n, disabled, onEscolher }: Props) {
  const [aberto, setAberto] = useState(false);

  return (
    <Popover open={aberto} onOpenChange={setAberto}>
      <PopoverTrigger
        className={buttonVariants({ variant: "ghost", size: "icon" })}
        aria-label={rotulo}
        disabled={disabled}
      >
        <Smile className="size-4" aria-hidden />
      </PopoverTrigger>
      <PopoverContent
        side="top"
        align="start"
        className="w-[min(22rem,calc(100vw-2rem))] gap-0 overflow-hidden p-0"
      >
        {aberto && <SeletorEmojiCompleto i18n={i18n} onEscolher={onEscolher} />}
      </PopoverContent>
    </Popover>
  );
}
