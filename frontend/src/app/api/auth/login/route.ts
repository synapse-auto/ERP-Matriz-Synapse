import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

import { MAX_AGE_COOKIE_REFRESH_SEGUNDOS, NOME_COOKIE_REFRESH } from "@/lib/auth/constants";
import { obterUrlApiServidor } from "@/lib/api/server-api-url";

/**
 * Faz o papel de BFF para o login: chama o backend real (`POST /api/v1/auth/login`, E07), que
 * devolve `{accessToken, refreshToken, expiraEmSegundos}` no corpo — não como cookie. Aqui o
 * refreshToken vira cookie httpOnly e nunca chega ao browser; só accessToken/expiraEmSegundos
 * voltam na resposta, para o Zustand guardar em memória.
 */
export async function POST(requisicao: NextRequest) {
  const corpo = await requisicao.json();

  const respostaBackend = await fetch(`${obterUrlApiServidor()}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
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
