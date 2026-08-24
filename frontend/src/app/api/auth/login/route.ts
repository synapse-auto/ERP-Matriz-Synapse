import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

import {
  NOME_COOKIE_PERSISTENCIA_SESSAO,
  NOME_COOKIE_REFRESH,
} from "@/lib/auth/constants";
import { opcoesCookieRefresh } from "@/lib/auth/cookie-refresh";
import { obterUrlApiServidor } from "@/lib/api/server-api-url";

/**
 * Faz o papel de BFF para o login: chama o backend real (`POST /api/v1/auth/login`, E07), que
 * devolve `{accessToken, refreshToken, expiraEmSegundos}` no corpo — não como cookie. Aqui o
 * refreshToken vira cookie httpOnly e nunca chega ao browser; só accessToken/expiraEmSegundos
 * voltam na resposta, para o Zustand guardar em memória.
 */
export async function POST(requisicao: NextRequest) {
  const corpo = (await requisicao.json()) as Record<string, unknown>;
  const { manterSessaoAtiva, ...credenciais } = corpo;
  const sessaoPersistente = manterSessaoAtiva === true;

  const respostaBackend = await fetch(`${obterUrlApiServidor()}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(credenciais),
  });

  if (!respostaBackend.ok) {
    const problema = await respostaBackend.json().catch(() => null);
    return NextResponse.json(problema ?? { detail: "Falha de autenticação" }, {
      status: respostaBackend.status,
    });
  }

  const sessao = (await respostaBackend.json()) as {
    accessToken: string;
    refreshToken: string;
    expiraEmSegundos: number;
  };

  const cookieStore = await cookies();
  cookieStore.set(NOME_COOKIE_REFRESH, sessao.refreshToken, opcoesCookieRefresh(sessaoPersistente));
  cookieStore.set(
    NOME_COOKIE_PERSISTENCIA_SESSAO,
    sessaoPersistente ? "1" : "0",
    opcoesCookieRefresh(sessaoPersistente),
  );

  return NextResponse.json({
    accessToken: sessao.accessToken,
    expiraEmSegundos: sessao.expiraEmSegundos,
  });
}
