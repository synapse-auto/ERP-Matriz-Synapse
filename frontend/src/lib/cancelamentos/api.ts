import { apiFetch } from "@/lib/api/http-client";

export interface ConfiguracaoCancelamentos {
  fusoHorario: string;
  inicioPadrao: string;
  fimPadrao: string;
  tamanhoPagina: number;
  maximoPagina: number;
  maximoDias: number;
  maximoPaginas: number;
  maximoBusca: number;
}
export interface FiltrosCancelamentos {
  inicio: string;
  fim: string;
  base: "REGISTRO" | "AGENDAMENTO" | "CANCELAMENTO";
  descricao: "TODAS" | "COM_DESCRICAO" | "SEM_DESCRICAO";
  profissional: string;
  tipo: string;
  nome: string;
}
export interface Cancelamento {
  id: string;
  agendadoPara: string | null;
  registradoEm: string | null;
  recebidoEm?: string | null;
  canceladoEm?: string | null;
  leadId?: string | null;
  atendimentoId?: string | null;
  contatoNome: string | null;
  profissionalNome: string | null;
  tipoAgendamento: string | null;
  descricao: string | null;
  temDescricao?: boolean;
}
export interface PaginaCancelamentos { itens: Cancelamento[]; temProxima: boolean }
export interface DetalheCancelamento extends Cancelamento {
  identificacao?: { cpf: string | null; telefone: string | null } | null;
}

export const obterConfiguracaoCancelamentos = (signal?: AbortSignal) =>
  apiFetch<ConfiguracaoCancelamentos>("/api/v1/cancelamentos/configuracao", { signal });
export function listarCancelamentos(filtros: FiltrosCancelamentos, pagina: number, tamanho: number, signal?: AbortSignal) {
  const busca = new URLSearchParams({ ...filtros, pagina: String(pagina), tamanho: String(tamanho) });
  return apiFetch<PaginaCancelamentos>(`/api/v1/cancelamentos?${busca}`, { signal });
}
export const obterCancelamento = (id: string, signal?: AbortSignal) =>
  apiFetch<DetalheCancelamento>(`/api/v1/cancelamentos/${encodeURIComponent(id)}`, { signal });
