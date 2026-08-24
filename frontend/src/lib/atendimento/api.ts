import { ErroDeApi, type ProblemaHttp } from "@/lib/api/errors";
import { apiFetch } from "@/lib/api/http-client";
import { useAuthStore } from "@/lib/auth/auth-store";

import type {
  AtendimentoResumo,
  CartaoAtendimento,
  ConfiguracaoComposer,
  ContagemPorVisao,
  EnvioResposta,
  MensagemResposta,
  PaginaMensagens,
  ParticipanteAtendimento,
  PedidoEntradaAtendimento,
  TagResposta,
  UsuarioResposta,
  VisaoAtendimento,
} from "./types";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "";

export function listarAtendimentos(visao: VisaoAtendimento): Promise<CartaoAtendimento[]> {
  return apiFetch<CartaoAtendimento[]>(`/api/v1/atendimentos?visao=${visao}`);
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

export function enviarMensagem(leadId: string, conteudo: string): Promise<EnvioResposta> {
  return apiFetch<EnvioResposta>("/api/v1/atendimentos/mensagens", {
    method: "POST",
    body: JSON.stringify({ leadId, conteudo }),
  });
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
): Promise<EnvioResposta> {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append("arquivo", arquivo);
    if (legenda) {
      formData.append("legenda", legenda);
    }

    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_URL}/api/v1/atendimentos/${atendimentoId}/mensagens/midia`);
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

export function listarUsuarios(): Promise<UsuarioResposta[]> {
  return apiFetch<UsuarioResposta[]>("/api/v1/usuarios");
}

export function listarTags(): Promise<TagResposta[]> {
  return apiFetch<TagResposta[]>("/api/v1/tags");
}
