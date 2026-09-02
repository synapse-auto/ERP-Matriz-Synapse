import { ErroDeApi, type ProblemaHttp } from "@/lib/api/errors";
import { apiFetch } from "@/lib/api/http-client";
import { useAuthStore } from "@/lib/auth/auth-store";

import type {
  AtendimentoResumo,
  CapacidadeDoCanal,
  CartaoAtendimento,
  CategoriaTemplateWhatsApp,
  ItemInbox,
  ConfiguracaoComposer,
  ContagemPorVisao,
  EnvioResposta,
  FinalizacaoEmLotePrevia,
  FinalizacaoEmLoteResposta,
  MensagemResposta,
  NovoContatoResposta,
  PaginaMensagens,
  PedidoDeNovoContato,
  ParticipanteAtendimento,
  PedidoEntradaAtendimento,
  ResumoReacao,
  TagResposta,
  TemplateWhatsApp,
  UsuarioResposta,
  DestinoDeTransferencia,
  VisaoAtendimento,
} from "./types";

export interface PaginaInbox {
  itens: ItemInbox[];
  proximoCursor: string | null;
}

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "";

export function listarAtendimentos(visao: VisaoAtendimento): Promise<CartaoAtendimento[]> {
  return apiFetch<CartaoAtendimento[]>(`/api/v1/atendimentos?visao=${visao}`);
}

export async function listarInboxUnificada(
  visao: VisaoAtendimento,
  cursor?: string | null,
  limite = 50,
): Promise<PaginaInbox> {
  const params = new URLSearchParams({ visao, limite: String(limite) });
  if (cursor) params.set("cursor", cursor);
  const pagina = await apiFetch<PaginaInbox>(`/api/v1/atendimentos/inbox?${params.toString()}`);
  return {
    ...pagina,
    itens: pagina.itens.map(normalizarItemInbox),
  };
}

/** Compatibilidade durante atualização gradual: a primeira versão da inbox usou `nome` no cliente. */
function normalizarItemInbox(item: ItemInbox): ItemInbox {
  if (item.tipo === "EQUIPE_INTERNA") return item;
  return {
    ...item,
    leadNome: item.leadNome || item.nome || "?",
    leadFotoUrl: item.leadFotoUrl ?? item.avatarUrl ?? null,
    atendimentoAtivoId:
      item.atendimentoAtivoId ?? (item.status === "FINALIZADO" ? null : item.atendimentoId),
    ultimaMensagemRemetenteTipo: item.ultimaMensagemRemetenteTipo ?? null,
    ultimaMensagemDoLeadEm: item.ultimaMensagemDoLeadEm ?? null,
  };
}

/** Os badges das abas — uma contagem por visão, na mesma chamada (E17b §Bloco 6). */
export function contarAtendimentosPorVisao(): Promise<ContagemPorVisao> {
  return apiFetch<ContagemPorVisao>("/api/v1/atendimentos/contagem");
}

export function marcarAtendimentoComoLido(atendimentoId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/atendimentos/${atendimentoId}/leitura`, {
    method: "POST",
  });
}

export function definirReacao(
  atendimentoId: string,
  mensagemId: string,
  enviadoEm: string,
  emoji: string,
): Promise<{ mensagemId: string; enviadoEm: string; reacoes: ResumoReacao[] }> {
  return apiFetch(
    `/api/v1/atendimentos/${atendimentoId}/mensagens/${mensagemId}/reacao?enviadoEm=${encodeURIComponent(enviadoEm)}`,
    { method: "PUT", body: JSON.stringify({ emoji }) },
  );
}

export function removerReacao(
  atendimentoId: string,
  mensagemId: string,
  enviadoEm: string,
): Promise<{ mensagemId: string; enviadoEm: string; reacoes: ResumoReacao[] }> {
  return apiFetch(
    `/api/v1/atendimentos/${atendimentoId}/mensagens/${mensagemId}/reacao?enviadoEm=${encodeURIComponent(enviadoEm)}`,
    { method: "DELETE" },
  );
}

/** `desde` ausente traz a conversa inteira — primeira carga da tela. */
export function paginaMensagens(
  atendimentoId: string,
  cursor: string | null,
): Promise<PaginaMensagens> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : "";
  return apiFetch<PaginaMensagens>(`/api/v1/atendimentos/${atendimentoId}/mensagens${query}`);
}

export function mensagensDesde(atendimentoId: string, desde: string): Promise<MensagemResposta[]> {
  return apiFetch<MensagemResposta[]>(
    `/api/v1/atendimentos/${atendimentoId}/mensagens/desde?desde=${encodeURIComponent(desde)}`,
  );
}

export interface AlvoDeResposta {
  mensagemId: string;
  enviadoEm: string;
}

export function enviarMensagem(
  leadId: string,
  conteudo: string,
  resposta?: AlvoDeResposta,
): Promise<EnvioResposta> {
  return apiFetch<EnvioResposta>("/api/v1/atendimentos/mensagens", {
    method: "POST",
    body: JSON.stringify({
      leadId,
      conteudo,
      ...(resposta
        ? { mensagemOrigemId: resposta.mensagemId, origemEnviadaEm: resposta.enviadoEm }
        : {}),
    }),
  });
}

export function iniciarNovoContato(pedido: PedidoDeNovoContato): Promise<NovoContatoResposta> {
  return apiFetch<NovoContatoResposta>("/api/v1/atendimentos/novo-contato", {
    method: "POST",
    body: JSON.stringify(pedido),
  });
}

export function obterCapacidadeDoCanal(): Promise<CapacidadeDoCanal> {
  return apiFetch<CapacidadeDoCanal>("/api/v1/config/canal");
}

export function abrirAtendimentoParaLead(leadId: string): Promise<NovoContatoResposta> {
  return apiFetch<NovoContatoResposta>(`/api/v1/atendimentos/leads/${leadId}/novo`, {
    method: "POST",
  });
}

export function enviarTemplate(
  leadId: string,
  nome: string,
  idioma: string,
  parametros: string[] = [],
): Promise<EnvioResposta> {
  return apiFetch<EnvioResposta>("/api/v1/atendimentos/mensagens/template", {
    method: "POST",
    body: JSON.stringify({ leadId, nome, idioma, parametros }),
  });
}

export function listarTemplatesWhatsApp(): Promise<TemplateWhatsApp[]> {
  return apiFetch<TemplateWhatsApp[]>("/api/v1/whatsapp/templates");
}

export function criarTemplateWhatsApp(pedido: {
  nome: string;
  idioma: string;
  categoria: CategoriaTemplateWhatsApp;
  corpo: string;
}): Promise<TemplateWhatsApp> {
  return apiFetch<TemplateWhatsApp>("/api/v1/whatsapp/templates", {
    method: "POST",
    body: JSON.stringify(pedido),
  });
}

export function encaminharMensagem(
  origemAtendimentoId: string,
  mensagemId: string,
  enviadoEm: string,
  destinoAtendimentoId: string,
): Promise<EnvioResposta> {
  return apiFetch<EnvioResposta>(
    `/api/v1/atendimentos/${origemAtendimentoId}/mensagens/${mensagemId}/encaminhamentos?enviadoEm=${encodeURIComponent(enviadoEm)}`,
    { method: "POST", body: JSON.stringify({ destinoAtendimentoId }) },
  );
}

export function obterConfiguracaoComposer(): Promise<ConfiguracaoComposer> {
  return apiFetch<ConfiguracaoComposer>("/api/v1/atendimentos/configuracao-composer");
}

/**
 * Upload de anexo — via `XMLHttpRequest`, não `fetch`: é o único jeito de reportar progresso real
 * de upload (evento `progress` do `xhr.upload`). Mesma autenticação e o mesmo parsing de erro RFC
 * 7807 de `apiFetch`, montados na mão porque `fetch` não expõe esse evento.
 */
export function enviarMidia(
  atendimentoId: string,
  arquivo: File,
  legenda: string | undefined,
  onProgresso: (percentual: number) => void,
  resposta?: AlvoDeResposta,
): Promise<EnvioResposta> {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append("arquivo", arquivo);
    if (legenda) {
      formData.append("legenda", legenda);
    }

    const params = new URLSearchParams();
    if (resposta) {
      params.set("mensagemOrigemId", resposta.mensagemId);
      params.set("origemEnviadaEm", resposta.enviadoEm);
    }
    const query = params.toString() ? `?${params.toString()}` : "";
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_URL}/api/v1/atendimentos/${atendimentoId}/mensagens/midia${query}`);
    const accessToken = useAuthStore.getState().accessToken;
    if (accessToken) {
      xhr.setRequestHeader("Authorization", `Bearer ${accessToken}`);
    }

    xhr.upload.onprogress = (evento) => {
      if (evento.lengthComputable) {
        onProgresso(Math.round((evento.loaded / evento.total) * 100));
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText) as EnvioResposta);
        return;
      }
      const problema = parseProblemaHttp(xhr.responseText);
      reject(new ErroDeApi(xhr.status, problema, `Erro ${xhr.status} ao enviar anexo`));
    };

    xhr.onerror = () => reject(new ErroDeApi(0, null, "Falha de rede ao enviar anexo"));

    xhr.send(formData);
  });
}

function parseProblemaHttp(corpo: string): ProblemaHttp | null {
  try {
    return JSON.parse(corpo) as ProblemaHttp;
  } catch {
    return null;
  }
}

export function transferirAtendimento(
  atendimentoId: string,
  paraAtendenteId: string | null,
): Promise<AtendimentoResumo> {
  return apiFetch<AtendimentoResumo>(`/api/v1/atendimentos/${atendimentoId}/transferir`, {
    method: "POST",
    body: JSON.stringify({ paraAtendenteId }),
  });
}

export function finalizarAtendimento(atendimentoId: string): Promise<AtendimentoResumo> {
  return apiFetch<AtendimentoResumo>(`/api/v1/atendimentos/${atendimentoId}/finalizar`, {
    method: "POST",
  });
}

export function contarAtendimentosFinalizaveis(): Promise<FinalizacaoEmLotePrevia> {
  return apiFetch<FinalizacaoEmLotePrevia>("/api/v1/atendimentos/finalizar-lote");
}

export function finalizarAtendimentosVisiveis(): Promise<FinalizacaoEmLoteResposta> {
  return apiFetch<FinalizacaoEmLoteResposta>("/api/v1/atendimentos/finalizar-lote", {
    method: "POST",
  });
}

export function listarParticipantes(atendimentoId: string): Promise<ParticipanteAtendimento[]> {
  return apiFetch<ParticipanteAtendimento[]>(`/api/v1/atendimentos/${atendimentoId}/participantes`);
}

export function obterMeuPedido(atendimentoId: string): Promise<PedidoEntradaAtendimento | null> {
  return apiFetch<PedidoEntradaAtendimento | null>(`/api/v1/atendimentos/${atendimentoId}/pedido-entrada/meu`);
}

export function listarPedidosPendentes(atendimentoId: string): Promise<PedidoEntradaAtendimento[]> {
  return apiFetch<PedidoEntradaAtendimento[]>(`/api/v1/atendimentos/${atendimentoId}/pedidos-entrada`);
}

export function pedirEntrada(atendimentoId: string) { return apiFetch(`/api/v1/atendimentos/${atendimentoId}/pedir-entrada`, { method: "POST" }); }
export function entrarAtendimento(atendimentoId: string) { return apiFetch(`/api/v1/atendimentos/${atendimentoId}/entrar`, { method: "POST" }); }
export function sairAtendimento(atendimentoId: string) { return apiFetch(`/api/v1/atendimentos/${atendimentoId}/sair`, { method: "POST" }); }
export function aprovarPedido(pedidoId: string) { return apiFetch(`/api/v1/atendimentos/pedidos-entrada/${pedidoId}/aprovar`, { method: "POST" }); }
export function recusarPedido(pedidoId: string) { return apiFetch(`/api/v1/atendimentos/pedidos-entrada/${pedidoId}/recusar`, { method: "POST" }); }

export function listarDestinosDeTransferencia(): Promise<DestinoDeTransferencia[]> {
  return apiFetch<DestinoDeTransferencia[]>("/api/v1/atendimentos/destinos-de-transferencia");
}

export function listarUsuarios(): Promise<UsuarioResposta[]> {
  return apiFetch<UsuarioResposta[]>("/api/v1/usuarios");
}

export interface AvaliacaoAtendimento {
  id: string;
  atendimentoId: string;
  atendenteId: string;
  nota: number;
  comentario: string | null;
  criadoEm: string;
}

export async function obterAvaliacao(atendimentoId: string): Promise<AvaliacaoAtendimento | null> {
  try {
    return await apiFetch<AvaliacaoAtendimento>(`/api/v1/atendimentos/${atendimentoId}/avaliacao`);
  } catch (erro) {
    if (erro instanceof ErroDeApi && erro.status === 404) return null;
    throw erro;
  }
}

export function registrarAvaliacao(
  atendimentoId: string,
  nota: number,
  comentario?: string,
): Promise<AvaliacaoAtendimento> {
  return apiFetch<AvaliacaoAtendimento>(`/api/v1/atendimentos/${atendimentoId}/avaliacao`, {
    method: "POST",
    body: JSON.stringify({ nota, comentario }),
  });
}

export function listarTags(): Promise<TagResposta[]> {
  return apiFetch<TagResposta[]>("/api/v1/tags");
}
