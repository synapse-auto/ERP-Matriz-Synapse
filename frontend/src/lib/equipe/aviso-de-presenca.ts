import type { StatusPresenca } from "./types";

/**
 * Aviso PRESENCA_ALTERADA da fila pessoal: o SISTEMA mudou a presença do usuário (conexão ou desconexão, E223). Não
 * tem lead nem conversa e não vira cartão; só faz o rodapé refletir o estado sem F5. Fora do union de notificações de
 * atendimento de propósito, como `ACESSO_ALTERADO` e o aviso de finalização em massa.
 */
export interface AvisoDePresencaAlterada {
  tipo: "PRESENCA_ALTERADA";
  eventoId: string;
  dados: { status: StatusPresenca };
}

const ESTADOS: readonly StatusPresenca[] = ["ONLINE", "AUSENTE", "OFFLINE"];

export function ehAvisoDePresencaAlterada(valor: unknown): valor is AvisoDePresencaAlterada {
  if (typeof valor !== "object" || valor === null) return false;
  const candidato = valor as { tipo?: unknown; dados?: unknown };
  if (candidato.tipo !== "PRESENCA_ALTERADA") return false;
  if (typeof candidato.dados !== "object" || candidato.dados === null) return false;
  const status = (candidato.dados as { status?: unknown }).status;
  return typeof status === "string" && (ESTADOS as readonly string[]).includes(status);
}
