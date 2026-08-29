"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Tempos da expansão temporária: a intenção evita abrir ao atravessar a barra;
 * o atraso de fechar cobre jitter na borda e o caminho até o botão/popup.
 * A duração é mais lenta que o duration-200 da E84 para a largura não parecer um estalo.
 */
export const EXPANSAO_DA_SIDEBAR = {
  larguraRetraidaPx: 76,
  larguraExpandidaPx: 260,
  intencaoAbrirMs: 160,
  atrasoFecharMs: 240,
  duracaoAnimacaoMs: 360,
  easing: "cubic-bezier(0.22, 1, 0.36, 1)",
} as const;

export function estiloDaLarguraDaSidebar(expandida: boolean): {
  width: number;
  transition: string;
} {
  return {
    width: expandida
      ? EXPANSAO_DA_SIDEBAR.larguraExpandidaPx
      : EXPANSAO_DA_SIDEBAR.larguraRetraidaPx,
    transition: `width ${EXPANSAO_DA_SIDEBAR.duracaoAnimacaoMs}ms ${EXPANSAO_DA_SIDEBAR.easing}`,
  };
}

export function useExpansaoDaSidebar() {
  const [fixada, setFixada] = useState(false);
  const [temporaria, setTemporaria] = useState(false);
  const ponteiroDentro = useRef(false);
  const timerAbrir = useRef<ReturnType<typeof setTimeout> | null>(null);
  const timerFechar = useRef<ReturnType<typeof setTimeout> | null>(null);

  const limparTimers = useCallback(() => {
    if (timerAbrir.current !== null) {
      clearTimeout(timerAbrir.current);
      timerAbrir.current = null;
    }
    if (timerFechar.current !== null) {
      clearTimeout(timerFechar.current);
      timerFechar.current = null;
    }
  }, []);

  useEffect(() => limparTimers, [limparTimers]);

  const aoPonteiroEntrar = useCallback(() => {
    ponteiroDentro.current = true;
    if (timerFechar.current !== null) {
      clearTimeout(timerFechar.current);
      timerFechar.current = null;
    }
    if (fixada || temporaria || timerAbrir.current !== null) {
      return;
    }
    timerAbrir.current = setTimeout(() => {
      timerAbrir.current = null;
      setTemporaria(true);
    }, EXPANSAO_DA_SIDEBAR.intencaoAbrirMs);
  }, [fixada, temporaria]);

  const aoPonteiroSair = useCallback(() => {
    ponteiroDentro.current = false;
    if (timerAbrir.current !== null) {
      clearTimeout(timerAbrir.current);
      timerAbrir.current = null;
    }
    if (fixada || timerFechar.current !== null) {
      return;
    }
    timerFechar.current = setTimeout(() => {
      timerFechar.current = null;
      setTemporaria(false);
    }, EXPANSAO_DA_SIDEBAR.atrasoFecharMs);
  }, [fixada]);

  const aoFocoDentro = useCallback(() => {
    limparTimers();
    setTemporaria(true);
  }, [limparTimers]);

  const aoFocoFora = useCallback(() => {
    if (fixada || ponteiroDentro.current) {
      return;
    }
    limparTimers();
    setTemporaria(false);
  }, [fixada, limparTimers]);

  const alternarFixacao = useCallback(() => {
    limparTimers();
    setFixada((atual) => {
      const proxima = !atual;
      setTemporaria(proxima ? false : ponteiroDentro.current);
      return proxima;
    });
  }, [limparTimers]);

  return {
    fixada,
    expandida: fixada || temporaria,
    aoPonteiroEntrar,
    aoPonteiroSair,
    aoFocoDentro,
    aoFocoFora,
    alternarFixacao,
  };
}
