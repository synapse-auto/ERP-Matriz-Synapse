import { apiFetch, apiFetchArquivo } from "@/lib/api/http-client";

import type {
  AtualizacaoDeConfiguracao,
  Campanha,
  ConfiguracaoDeCampanhas,
  ContatoExcluido,
  DetalheDaCampanha,
  Destinatario,
  FiltroDePublico,
  ListaDeCampanhas,
  MotivoDoDestinatario,
  PaginaDe,
  PedidoDeCampanha,
  PedidoDeProjecao,
  PreviaDoPublico,
  ProjecaoDeEnvio,
  ResultadoDoTeste,
  StatusDoDestinatario,
  TemplateParaCampanha,
} from "./types";

const BASE = "/api/v1/campanhas";

function enviando(metodo: "POST" | "PUT", corpo: unknown): { method: string; body: string } {
  return { method: metodo, body: JSON.stringify(corpo) };
}

export function listarCampanhas(pagina: number, tamanho: number): Promise<ListaDeCampanhas> {
  return apiFetch<ListaDeCampanhas>(`${BASE}?pagina=${pagina}&tamanho=${tamanho}`);
}

export function listarTemplatesParaCampanha(): Promise<TemplateParaCampanha[]> {
  return apiFetch<TemplateParaCampanha[]>(`${BASE}/templates`);
}

export function obterCampanha(id: string): Promise<DetalheDaCampanha> {
  return apiFetch<DetalheDaCampanha>(`${BASE}/${id}`);
}

export interface FiltroDeDestinatarios {
  status?: StatusDoDestinatario | null;
  motivo?: MotivoDoDestinatario | null;
}

export function listarDestinatarios(
  id: string,
  filtro: FiltroDeDestinatarios,
  pagina: number,
  tamanho: number,
): Promise<PaginaDe<Destinatario>> {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) });
  if (filtro.status) parametros.set("status", filtro.status);
  if (filtro.motivo) parametros.set("motivo", filtro.motivo);
  return apiFetch<PaginaDe<Destinatario>>(`${BASE}/${id}/destinatarios?${parametros.toString()}`);
}

export function listarConferencia(id: string, pagina: number, tamanho: number): Promise<PaginaDe<Destinatario>> {
  return apiFetch<PaginaDe<Destinatario>>(`${BASE}/${id}/conferencia?pagina=${pagina}&tamanho=${tamanho}`);
}

/** CSV autenticado: um link não leva o Bearer, então o arquivo é baixado como blob. */
export function baixarDestinatariosCsv(id: string): Promise<{ blob: Blob; nome: string | null }> {
  return apiFetchArquivo(`${BASE}/${id}/destinatarios.csv`);
}

export function preverPublico(filtro: Partial<FiltroDePublico>): Promise<PreviaDoPublico> {
  return apiFetch<PreviaDoPublico>(`${BASE}/previa`, enviando("POST", { filtro }));
}

export function listarExcluidos(
  filtro: Partial<FiltroDePublico>,
  motivo: MotivoDoDestinatario,
): Promise<ContatoExcluido[]> {
  return apiFetch<ContatoExcluido[]>(`${BASE}/previa/excluidos`, enviando("POST", { filtro, motivo }));
}

export function projetarEnvio(pedido: PedidoDeProjecao): Promise<ProjecaoDeEnvio> {
  return apiFetch<ProjecaoDeEnvio>(`${BASE}/projecao`, enviando("POST", pedido));
}

export function criarCampanha(pedido: PedidoDeCampanha): Promise<Campanha> {
  return apiFetch<Campanha>(BASE, enviando("POST", pedido));
}

export function atualizarRascunho(id: string, pedido: PedidoDeCampanha): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}`, enviando("PUT", pedido));
}

export function enviarTeste(id: string, telefone: string, destinatarioAutorizou: boolean): Promise<ResultadoDoTeste> {
  return apiFetch<ResultadoDoTeste>(`${BASE}/${id}/teste`, enviando("POST", { telefone, destinatarioAutorizou }));
}

export function iniciarCampanha(id: string): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}/iniciar`, { method: "POST" });
}

export function pausarCampanha(id: string): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}/pausar`, { method: "POST" });
}

export function retomarCampanha(id: string): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}/retomar`, { method: "POST" });
}

export function cancelarCampanha(id: string): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}/cancelar`, { method: "POST" });
}

export function alterarLimite(id: string, limiteDiario: number, ritmoPorMinuto: number | null): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}/limite`, enviando("PUT", { limiteDiario, ritmoPorMinuto }));
}

export function alterarInterruptor(id: string, desligada: boolean): Promise<Campanha> {
  return apiFetch<Campanha>(`${BASE}/${id}/interruptor`, enviando("PUT", { desligada }));
}

export function marcarComoConferido(id: string, destinatarioId: string): Promise<void> {
  return apiFetch<void>(`${BASE}/${id}/conferencia/${destinatarioId}/conferido`, { method: "POST" });
}

export function obterConfiguracaoDeCampanhas(): Promise<ConfiguracaoDeCampanhas> {
  return apiFetch<ConfiguracaoDeCampanhas>(`${BASE}/configuracao`);
}

export function atualizarConfiguracaoDeCampanhas(
  atualizacao: AtualizacaoDeConfiguracao,
): Promise<ConfiguracaoDeCampanhas> {
  return apiFetch<ConfiguracaoDeCampanhas>(`${BASE}/configuracao`, enviando("PUT", atualizacao));
}
