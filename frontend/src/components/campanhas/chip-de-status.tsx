"use client";

import {
  Ban,
  CalendarClock,
  CircleCheck,
  FileText,
  Pause,
  Send,
  ShieldAlert,
  type LucideIcon,
} from "lucide-react";

import { useTextos } from "@/lib/config/textos-provider";
import type { StatusDaCampanha } from "@/lib/campanhas/types";
import { cn } from "@/lib/utils";

type Tom = "sucesso" | "atencao" | "info" | "erro" | "neutro";

/** Fundo e borda tingidos; o texto fica na cor do corpo (AA) e o ícone leva a cor do status. */
const APARENCIA: Record<StatusDaCampanha, { tom: Tom; icone: LucideIcon }> = {
  RASCUNHO: { tom: "neutro", icone: FileText },
  AGENDADA: { tom: "info", icone: CalendarClock },
  EM_ANDAMENTO: { tom: "info", icone: Send },
  PAUSADA: { tom: "atencao", icone: Pause },
  CONCLUIDA: { tom: "sucesso", icone: CircleCheck },
  CANCELADA: { tom: "neutro", icone: Ban },
  PAUSADA_AUTOMATICAMENTE: { tom: "erro", icone: ShieldAlert },
};

const FUNDO: Record<Tom, string> = {
  sucesso: "border-cor-sucesso/30 bg-cor-sucesso/10",
  atencao: "border-cor-atencao/40 bg-cor-atencao/10",
  info: "border-cor-info/30 bg-cor-info/10",
  erro: "border-cor-erro/30 bg-cor-erro/10",
  neutro: "border-border bg-muted",
};

const ICONE: Record<Tom, string> = {
  sucesso: "text-cor-sucesso",
  atencao: "text-cor-atencao",
  info: "text-cor-info",
  erro: "text-cor-erro",
  neutro: "text-muted-foreground",
};

/** Status por cor E ícone E texto: nunca depende só da cor. */
export function ChipDeStatus({ status }: { status: StatusDaCampanha }) {
  const textos = useTextos().campanhas.status;
  const { tom, icone: Icone } = APARENCIA[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-bold text-foreground",
        FUNDO[tom],
      )}
    >
      <Icone className={cn("size-3", ICONE[tom])} aria-hidden />
      {textos[status]}
    </span>
  );
}
