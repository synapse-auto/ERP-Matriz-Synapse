import type { ReactNode } from "react";

/**
 * Pill de status colorida (E17b §Bloco 4) — mesmo padrão nas quatro telas do bloco (papel/presença
 * em Equipe, status de lembrete, tipo de mídia em Mensagens Rápidas, status de mensagem programada).
 * Extraída na segunda tela que precisou disso (Lembretes).
 *
 * <p>Cada tom mapeia para os tokens semânticos de design/TOKENS.md — nunca cor literal.
 */
const TONS = {
  sucesso: "bg-cor-sucesso/10 text-cor-sucesso",
  atencao: "bg-cor-atencao/10 text-cor-atencao",
  ia: "bg-cor-ia/10 text-cor-ia",
  info: "bg-cor-info/10 text-cor-info",
  erro: "bg-cor-erro/10 text-cor-erro",
  neutro: "bg-muted text-muted-foreground",
} as const;

export type TomDePill = keyof typeof TONS;

type Props = {
  tom: TomDePill;
  children: ReactNode;
  icone?: ReactNode;
  className?: string;
};

export function PillDeStatus({ tom, children, icone, className }: Props) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-bold ${TONS[tom]} ${className ?? ""}`}
    >
      {icone}
      {children}
    </span>
  );
}
