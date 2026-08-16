"use client";

import { useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";

import { renovarAccessToken } from "@/lib/api/http-client";

import { useAuthStore } from "./auth-store";

/** Renova 30s antes de expirar — margem para a chamada de renovação em si não perder a janela. */
const MARGEM_REFRESH_MS = 30_000;

/**
 * Hidrata o access token em memória a partir do cookie httpOnly no mount (uma recarga de página
 * perde tudo que está em memória — é o preço de não usar localStorage) e agenda a renovação
 * proativa antes de expirar. Se a renovação falhar (cookie ausente ou revogado), limpa a sessão e
 * manda para /login — mas só se ainda não estiver lá, para não entrar em loop de redirecionamento.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const expiraEm = useAuthStore((estado) => estado.expiraEm);
  const status = useAuthStore((estado) => estado.status);
  const limparSessao = useAuthStore((estado) => estado.limparSessao);
  const router = useRouter();
  const pathname = usePathname();
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function irParaLogin() {
    limparSessao();
    if (pathname !== "/login") {
      router.replace("/login");
    }
  }

  useEffect(() => {
    // O provider envolve também /login. Ali ainda não há cookie e chamar refresh só produz um
    // 401 esperado no console; o LoginForm já grava a sessão em memória quando autentica.
    if (pathname === "/login" || status !== "carregando") return;

    let cancelado = false;
    renovarAccessToken().then((renovou) => {
      if (cancelado) return;
      if (!renovou) {
        irParaLogin();
      }
    });
    return () => {
      cancelado = true;
    };
    // Hidrata ao entrar em uma rota protegida. O status evita renovar outra vez após o login,
    // quando o LoginForm já gravou a sessão e a navegação preservou este provider.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname, status]);

  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    if (!expiraEm) return;

    const atraso = Math.max(expiraEm - Date.now() - MARGEM_REFRESH_MS, 0);
    timerRef.current = setTimeout(async () => {
      const renovou = await renovarAccessToken();
      if (!renovou) {
        irParaLogin();
      }
    }, atraso);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expiraEm]);

  // O access token só existe em memória. Num reload, nenhum consumidor protegido pode montar antes
  // de o cookie httpOnly restaurá-lo: isso evita 401 transitório, queries em estado de erro e uma
  // primeira tentativa de WebSocket com `access_token=` vazio. Se a renovação falhar, a árvore
  // continua bloqueada durante o redirecionamento para /login.
  if (pathname !== "/login" && status !== "autenticado") {
    return null;
  }

  return children;
}
