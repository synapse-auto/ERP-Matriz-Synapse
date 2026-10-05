/**
 * Aviso FINALIZACAO_EM_MASSA_CONCLUIDA da fila pessoal. So chega a quem teve ao menos um atendimento finalizado
 * (o servidor decide); fica fora do union de notificacoes de atendimento porque nao tem lead nem conversa.
 * `eventoId` e igual em todo reenvio da mesma operacao: a tela deduplica por ele.
 */
export interface AvisoDeFinalizacaoEmMassa {
  tipo: "FINALIZACAO_EM_MASSA_CONCLUIDA";
  eventoId: string;
  dados: {
    operacaoId: string;
    finalizadosDoUsuario: number;
    totalFinalizados: number;
    ignorados: number;
    falhas: number;
    parcial: boolean;
    afetados: { nome: string; finalizados: number }[];
  };
}

export function ehAvisoDeFinalizacaoEmMassa(valor: unknown): valor is AvisoDeFinalizacaoEmMassa {
  if (typeof valor !== "object" || valor === null) return false;
  const candidato = valor as { tipo?: unknown; eventoId?: unknown; dados?: unknown };
  return (
    candidato.tipo === "FINALIZACAO_EM_MASSA_CONCLUIDA" &&
    typeof candidato.eventoId === "string" &&
    typeof candidato.dados === "object" &&
    candidato.dados !== null &&
    Array.isArray((candidato.dados as { afetados?: unknown }).afetados)
  );
}

/** "Clayton", "Clayton e Nayara", "Clayton, Nayara e Bruno" (pt-BR). */
export function listaDeNomes(nomes: string[]): string {
  return new Intl.ListFormat("pt-BR", { style: "long", type: "conjunction" }).format(nomes);
}
