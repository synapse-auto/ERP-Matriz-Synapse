import { ErroDeApi, type ProblemaHttp } from "@/lib/api/errors";
import { apiFetch } from "@/lib/api/http-client";
import { useAuthStore } from "@/lib/auth/auth-store";

import type {
  AtendimentoResumo,
  CartaoAtendimento,
  EnvioResposta,
  MensagemResposta,
  TagResposta,
  UsuarioResposta,
  VisaoAtendimento,
} from "./types";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

export function listarAtendimentos(visao: VisaoAtendimento): Promise<CartaoAtendimento[]> {
  return apiFetch<CartaoAtendimento[]>(`/api/v1/atendimentos?visao=${visao}`);
}

/** `desde` ausente traz a conversa inteira — primeira carga da tela. */
export function mensagensDesde(atendimentoId: string, desde?: string): Promise<MensagemResposta[]> {
  const query = desde ? `?desde=${encodeURIComponent(desde)}` : "";
  return apiFetch<MensagemResposta[]>(`/api/v1/atendimentos/${atendimentoId}/mensagens${query}`);
}

export function enviarMensagem(leadId: string, conteudo: string): Promise<EnvioResposta> {
  return apiFetch<EnvioResposta>("/api/v1/atendimentos/mensagens", {
    method: "POST",
    body: JSON.stringify({ leadId, conteudo }),
  });
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

export function listarUsuarios(): Promise<UsuarioResposta[]> {
  return apiFetch<UsuarioResposta[]>("/api/v1/usuarios");
}

export function listarTags(): Promise<TagResposta[]> {
  return apiFetch<TagResposta[]>("/api/v1/tags");
}
