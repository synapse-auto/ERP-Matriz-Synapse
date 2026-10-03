"use client";

import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";

/**
 * A mesma capacidade efetiva da Gestão protege o menu, a rota e os casos de uso do backend.
 */
export function ExigeAcessoACampanhas({ children }: { children: React.ReactNode }) {
  return <ExigeCapacidade capacidade="campanhas.ver">{children}</ExigeCapacidade>;
}
