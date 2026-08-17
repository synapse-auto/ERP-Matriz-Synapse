import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

import { MAX_AGE_COOKIE_REFRESH_SEGUNDOS, NOME_COOKIE_REFRESH } from "@/lib/auth/constants";
import { obterUrlApiServidor } from "@/lib/api/server-api-url";

/**
 * BFF de `POST /api/v1/auth/senha` (E29), no mesmo papel de `app/api/auth/login`: o backend devolve
 * `{accessToken, refreshToken, expiraEmSegundos}` no corpo, e é aqui que o refreshToken vira cookie
 * httpOnly de novo — a troca de senha emite uma sessão NOVA (RefreshTokenRepositorio.revogarTodosDoUsuario
 * derruba a antiga), então o cookie precisa ser reescrito exatamente como no login/refresh.
 *
 * Diferença do login: esta chamada exige um Bearer token válido (o usuário já está autenticado). O
 * access token só existe em memória no browser (Zustand), nunca chega ao servidor Next.js sozinho —
 * por isso o cliente reenvia o cabeçalho Authorization, e este handler só repassa.
 */
export async function POST(requisicao: NextRequest) {
  const autorizacao = requisicao.headers.get("authorization");
  const corpo = await requisicao.json();

  const respostaBackend = await fetch(`${obterUrlApiServidor()}/api/v1/auth/senha`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(autorizacao ? { Authorization: autorizacao } : {}),
    },
    body: JSON.stringify(corpo),
  });

  if (!respostaBackend.ok) {
    const problema = await respostaBackend.json().catch(() => null);
    return NextResponse.json(problema ?? { detail: "Não foi possível trocar a senha" }, {
      status: respostaBackend.status,
    });
  }

  const sessao = (await respostaBackend.json()) as {
    accessToken: string;
    refreshToken: string;
    expiraEmSegundos: number;
  };

  const cookieStore = await cookies();
  cookieStore.set(NOME_COOKIE_REFRESH, sessao.refreshToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: MAX_AGE_COOKIE_REFRESH_SEGUNDOS,
  });

  return NextResponse.json({
    accessToken: sessao.accessToken,
    expiraEmSegundos: sessao.expiraEmSegundos,
  });
}
