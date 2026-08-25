"use client";

import { useAuthStore } from "@/lib/auth/auth-store";

import { ErroDeApi, ProblemaHttp } from "./errors";

// Vazio em producao: browser chama a mesma origem e o Traefik encaminha /api ao backend.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "";

interface SessaoResposta {
  accessToken: string;
  expiraEmSegundos: number;
}

// Deduplica renovações concorrentes: se três chamadas tomam 401 ao mesmo tempo, só uma bate no
// /api/auth/refresh — as outras esperam a mesma promessa, em vez de rotacionar o refresh token
// três vezes (o que invalidaria as duas primeiras chamadas por reuso).
let renovacaoEmCurso: Promise<boolean> | null = null;

export async function renovarAccessToken(): Promise<boolean> {
  if (!renovacaoEmCurso) {
    renovacaoEmCurso = (async () => {
      try {
        const resposta = await fetch("/api/auth/refresh", { method: "POST" });
        if (!resposta.ok) return false;
        const sessao = (await resposta.json()) as SessaoResposta;
        useAuthStore.getState().definirSessao(sessao);
        return true;
      } catch {
        return false;
      } finally {
        renovacaoEmCurso = null;
      }
    })();
  }
  return renovacaoEmCurso;
}

function irParaLoginSemLoop() {
  useAuthStore.getState().limparSessao();
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}

interface OpcoesRequisicao extends RequestInit {
  /** Rotas públicas (login) não devem tentar renovar em 401 nem levar Authorization. */
  semAutorizacao?: boolean;
}

/**
 * Cliente HTTP central: cabeçalho de autorização, refresh-and-retry em 401 (uma única vez — se a
 * renovação também falhar, é sessão morta de verdade, não vale tentar de novo) e parsing de erro
 * RFC 7807, tudo num lugar só. Nenhuma tela deveria montar `fetch` com Authorization na mão.
 */
export async function apiFetch<T>(caminho: string, opcoes: OpcoesRequisicao = {}): Promise<T> {
  const { semAutorizacao, headers, ...resto } = opcoes;

  async function chamar(): Promise<Response> {
    const accessToken = useAuthStore.getState().accessToken;
    const cabecalhos = new Headers(headers);
    if (!cabecalhos.has("Content-Type") && resto.body && !(resto.body instanceof FormData)) {
      cabecalhos.set("Content-Type", "application/json");
    }
    if (!semAutorizacao && accessToken) {
      cabecalhos.set("Authorization", `Bearer ${accessToken}`);
    }
    return fetch(`${API_URL}${caminho}`, { ...resto, headers: cabecalhos });
  }

  let resposta = await chamar();

  if (resposta.status === 401 && !semAutorizacao) {
    const renovou = await renovarAccessToken();
    if (renovou) {
      resposta = await chamar();
    } else {
      irParaLoginSemLoop();
    }
  }

  if (!resposta.ok) {
    const problema = (await resposta.json().catch(() => null)) as ProblemaHttp | null;
    throw new ErroDeApi(resposta.status, problema, `Erro ${resposta.status} ao chamar ${caminho}`);
  }

  if (resposta.status === 204) {
    return undefined as T;
  }
  return (await resposta.json()) as T;
}

/** Mesmo fluxo de autorização/refresh para respostas binárias (fotos e outros arquivos). */
export async function apiFetchBlob(caminho: string): Promise<Blob> {
  const accessToken = useAuthStore.getState().accessToken;
  const cabecalhos = new Headers();
  if (accessToken) cabecalhos.set("Authorization", `Bearer ${accessToken}`);
  let resposta = await fetch(`${API_URL}${caminho}`, { headers: cabecalhos });
  if (resposta.status === 401) {
    const renovou = await renovarAccessToken();
    if (renovou) {
      const novoToken = useAuthStore.getState().accessToken;
      const novosCabecalhos = new Headers();
      if (novoToken) novosCabecalhos.set("Authorization", `Bearer ${novoToken}`);
      resposta = await fetch(`${API_URL}${caminho}`, { headers: novosCabecalhos });
    } else {
      irParaLoginSemLoop();
    }
  }
  if (!resposta.ok) throw new ErroDeApi(resposta.status, null, `Erro ${resposta.status} ao chamar ${caminho}`);
  return resposta.blob();
}
