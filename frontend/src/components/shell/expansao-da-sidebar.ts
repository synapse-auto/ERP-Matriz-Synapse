"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Hover temporário sobrepõe o chat (o slot fica em 76 px) para o layout não
 * recalcular a cada passagem. Intenção curta: a barra responde rápido, sem
 * abrir só de atravessar. O rótulo some primeiro no fechamento para não
 * “estourar” texto numa coluna estreita.
 */
export const EXPANSAO_DA_SIDEBAR = {
  larguraRetraidaPx: 76,
  larguraExpandidaPx: 260,
  intencaoAbrirMs: 50,
  atrasoFecharMs: 160,
  duracaoAnimacaoMs: 240,
  rotuloFadeAbrirMs: 160,
  rotuloFadeFecharMs: 90,
  rotuloFadeDelayAbrirMs: 70,
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

/** O chat só cede espaço quando a barra está fixada. Hover não empurra o conteúdo. */
export function estiloDaLarguraDoSlot(fixada: boolean): {
  width: number;
  transition: string;
} {
  return estiloDaLarguraDaSidebar(fixada);
}

export function estiloDoRotuloDaSidebar(retraida: boolean): {
  opacity: number;
  transition: string;
} {
  return {
    opacity: retraida ? 0 : 1,
    transition: retraida
      ? `opacity ${EXPANSAO_DA_SIDEBAR.rotuloFadeFecharMs}ms ${EXPANSAO_DA_SIDEBAR.easing}`
      : `opacity ${EXPANSAO_DA_SIDEBAR.rotuloFadeAbrirMs}ms ${EXPANSAO_DA_SIDEBAR.easing} ${EXPANSAO_DA_SIDEBAR.rotuloFadeDelayAbrirMs}ms`,
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

  const expandida = fixada || temporaria;
  return {
    fixada,
    expandida,
    sobreposta: expandida && !fixada,
    aoPonteiroEntrar,
    aoPonteiroSair,
    aoFocoDentro,
    aoFocoFora,
    alternarFixacao,
  };
}
