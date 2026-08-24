import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { NOME_COOKIE_PERSISTENCIA_SESSAO, NOME_COOKIE_REFRESH } from "@/lib/auth/constants";
import { obterUrlApiServidor } from "@/lib/api/server-api-url";

export async function POST() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(NOME_COOKIE_REFRESH)?.value;

  if (refreshToken) {
    // Best-effort: mesmo se o backend falhar em revogar, o cookie local é removido de qualquer
    // forma — o usuário não pode ficar preso numa sessão que ele mandou encerrar.
    await fetch(`${obterUrlApiServidor()}/api/v1/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    }).catch(() => null);
  }

  cookieStore.delete(NOME_COOKIE_REFRESH);
  cookieStore.delete(NOME_COOKIE_PERSISTENCIA_SESSAO);
  return new NextResponse(null, { status: 204 });
}
