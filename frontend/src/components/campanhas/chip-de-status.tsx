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

import { PillDeStatus } from "@/components/ui/pill-de-status";
import { useTextos } from "@/lib/config/textos-provider";
import type { StatusDaCampanha } from "@/lib/campanhas/types";

type Tom = "sucesso" | "atencao" | "ia" | "info" | "erro" | "neutro";

/** Cor E ícone: o status nunca depende só da cor (acessibilidade, daltonismo). */
const APARENCIA: Record<StatusDaCampanha, { tom: Tom; icone: LucideIcon }> = {
  RASCUNHO: { tom: "neutro", icone: FileText },
  AGENDADA: { tom: "info", icone: CalendarClock },
  EM_ANDAMENTO: { tom: "info", icone: Send },
  PAUSADA: { tom: "atencao", icone: Pause },
  CONCLUIDA: { tom: "sucesso", icone: CircleCheck },
  CANCELADA: { tom: "neutro", icone: Ban },
  PAUSADA_AUTOMATICAMENTE: { tom: "erro", icone: ShieldAlert },
};

export function ChipDeStatus({ status }: { status: StatusDaCampanha }) {
  const textos = useTextos().campanhas.status;
  const { tom, icone: Icone } = APARENCIA[status];
  return (
    <PillDeStatus tom={tom} icone={<Icone className="size-3" aria-hidden />}>
      {textos[status]}
    </PillDeStatus>
  );
}
