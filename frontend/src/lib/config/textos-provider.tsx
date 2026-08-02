"use client";

import { createContext, useContext } from "react";

import type { Textos } from "./schema";

const TextosContext = createContext<Textos | null>(null);

/**
 * O Server Component raiz (app/layout.tsx) busca textos.json e passa como prop serializável —
 * isto só empacota isso num Context para Client Components não precisarem receber `textos` como
 * prop em cada nível da árvore.
 */
export function TextosProvider({ textos, children }: { textos: Textos; children: React.ReactNode }) {
  return <TextosContext.Provider value={textos}>{children}</TextosContext.Provider>;
}

/** Nunca retorna undefined: sem Provider é erro de composição, não estado a tratar em runtime. */
export function useTextos(): Textos {
  const textos = useContext(TextosContext);
  if (!textos) {
    throw new Error("useTextos() chamado fora de <TextosProvider>");
  }
  return textos;
}
