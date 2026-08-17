"use client";

import { ErroDeApi, ProblemaHttp } from "@/lib/api/errors";

import { useAuthStore } from "./auth-store";

interface SessaoResposta {
  accessToken: string;
  expiraEmSegundos: number;
}

/**
 * Troca a própria senha (E29). Não usa {@link apiFetch} (que fala direto com o backend): esta
 * chamada precisa passar pela rota BFF `/api/auth/trocar-senha` para o refreshToken devolvido virar
 * cookie httpOnly, no mesmo papel de login/refresh. O `Authorization` é anexado manualmente aqui
 * porque `apiFetch` só existe para chamadas contra a API do backend, não para rotas same-origin do
 * próprio Next.js.
 */
export async function trocarSenha(senhaAtual: string, novaSenha: string): Promise<void> {
  const accessToken = useAuthStore.getState().accessToken;
  const resposta = await fetch("/api/auth/trocar-senha", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify({ senhaAtual, novaSenha }),
  });

  if (!resposta.ok) {
    const problema = (await resposta.json().catch(() => null)) as ProblemaHttp | null;
    throw new ErroDeApi(resposta.status, problema, "Não foi possível trocar a senha");
  }

  const sessao = (await resposta.json()) as SessaoResposta;
  // A resposta é uma sessão NOVA (access + refresh); adotá-la aqui é o que faz
  // `precisaTrocarSenha` virar false imediatamente, sem exigir novo login.
  useAuthStore.getState().definirSessao(sessao);
}
