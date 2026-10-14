"use client";

import { useCallback, useState, type SetStateAction } from "react";

/** Somente memória da aba, nunca storage persistente nem corpo de mensagem enviado ao servidor. */
const estados = new Map<string, unknown>();

export function limparEstadosRecuperaveis() { estados.clear(); }

export function useEstadoRecuperavel<T>(chave: string | null, inicial: T) {
  const [valor, definir] = useState<T>(() => chave && estados.has(chave) ? estados.get(chave) as T : inicial);
  const atualizar = useCallback((acao: SetStateAction<T>) => {
    definir((atual) => {
      const novo = typeof acao === "function" ? (acao as (anterior: T) => T)(atual) : acao;
      if (chave) estados.set(chave, novo);
      return novo;
    });
  }, [chave]);
  return [valor, atualizar] as const;
}
