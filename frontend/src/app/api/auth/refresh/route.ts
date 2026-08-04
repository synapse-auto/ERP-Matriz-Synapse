import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { MAX_AGE_COOKIE_REFRESH_SEGUNDOS, NOME_COOKIE_REFRESH } from "@/lib/auth/constants";
import { obterUrlApiServidor } from "@/lib/api/server-api-url";

/**
 * O backend rotaciona o refresh token a cada uso (RenovarSessaoUseCase): o token apresentado
 * morre, um novo nasce. Por isso este handler SEMPRE reescreve o cookie com o token novo — nunca
 * reaproveita o antigo. Uma chamada a este endpoint sem atualizar o cookie deixaria a sessão
 * inutilizável na próxima renovação (o token antigo já foi revogado no backend).
 */
export async function POST() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(NOME_COOKIE_REFRESH)?.value;

  if (!refreshToken) {
    return NextResponse.json({ detail: "Sem sessão para renovar" }, { status: 401 });
  }

  const respostaBackend = await fetch(`${obterUrlApiServidor()}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!respostaBackend.ok) {
    cookieStore.delete(NOME_COOKIE_REFRESH);
    const problema = await respostaBackend.json().catch(() => null);
    return NextResponse.json(problema ?? { detail: "Sessão expirada" }, {
      status: respostaBackend.status,
    });
  }

  const sessao = (await respostaBackend.json()) as {
    accessToken: string;
    refreshToken: string;
    expiraEmSegundos: number;
  };

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
