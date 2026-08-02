"use client";

import { useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";

import { useAuthStore } from "./auth-store";

/** Renova 30s antes de expirar — margem para a chamada de renovação em si não perder a janela. */
const MARGEM_REFRESH_MS = 30_000;

interface SessaoResposta {
  accessToken: string;
  expiraEmSegundos: number;
}

/**
 * Hidrata o access token em memória a partir do cookie httpOnly no mount (uma recarga de página
 * perde tudo que está em memória — é o preço de não usar localStorage) e agenda a renovação
 * proativa antes de expirar. Se a renovação falhar (cookie ausente ou revogado), limpa a sessão e
 * manda para /login — mas só se ainda não estiver lá, para não entrar em loop de redirecionamento.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const expiraEm = useAuthStore((estado) => estado.expiraEm);
  const definirSessao = useAuthStore((estado) => estado.definirSessao);
  const limparSessao = useAuthStore((estado) => estado.limparSessao);
  const router = useRouter();
  const pathname = usePathname();
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  async function renovar(): Promise<SessaoResposta | null> {
    try {
      const resposta = await fetch("/api/auth/refresh", { method: "POST" });
      if (!resposta.ok) return null;
      return (await resposta.json()) as SessaoResposta;
    } catch {
      return null;
    }
  }

  function irParaLogin() {
    limparSessao();
    if (pathname !== "/login") {
      router.replace("/login");
    }
  }

  useEffect(() => {
    let cancelado = false;
    renovar().then((sessao) => {
      if (cancelado) return;
      if (sessao) {
        definirSessao(sessao);
      } else {
        irParaLogin();
      }
    });
    return () => {
      cancelado = true;
    };
    // Só no mount: hidrata a partir do cookie. As renovações seguintes são o efeito abaixo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    if (!expiraEm) return;

    const atraso = Math.max(expiraEm - Date.now() - MARGEM_REFRESH_MS, 0);
    timerRef.current = setTimeout(async () => {
      const sessao = await renovar();
      if (sessao) {
        definirSessao(sessao);
      } else {
        irParaLogin();
      }
    }, atraso);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expiraEm]);

  return children;
}
