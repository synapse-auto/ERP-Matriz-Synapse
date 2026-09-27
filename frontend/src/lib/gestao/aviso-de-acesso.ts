/**
 * Aviso ACESSO_ALTERADO da fila pessoal (Gestão, docs/47). Fica fora do union de notificações de
 * atendimento de propósito: não tem atendimento, lead nem conteúdo — só "recarregue permissões".
 */
export interface AvisoDeAcessoAlterado {
  tipo: "ACESSO_ALTERADO";
  eventoId?: string;
  dados: { sessaoInvalidada: boolean; revisao: number };
}

export function ehAvisoDeAcessoAlterado(valor: unknown): valor is AvisoDeAcessoAlterado {
  return typeof valor === "object" && valor !== null && (valor as { tipo?: unknown }).tipo === "ACESSO_ALTERADO";
}
