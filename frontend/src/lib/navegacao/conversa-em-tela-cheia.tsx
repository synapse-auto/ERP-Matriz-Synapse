"use client";

import { createContext, useContext, useMemo, useState } from "react";

const Contexto = createContext<{
  ativa: boolean;
  definir: (ativa: boolean) => void;
}>({ ativa: false, definir: () => undefined });

export function ProvedorConversaEmTelaCheia({ children }: { children: React.ReactNode }) {
  const [ativa, definir] = useState(false);
  const valor = useMemo(() => ({ ativa, definir }), [ativa]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useConversaEmTelaCheia() {
  return useContext(Contexto);
}
