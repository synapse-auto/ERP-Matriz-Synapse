import { useAuthStore } from "@/lib/auth/auth-store";
import type { Capacidades, EstadoDasCapacidades } from "@/lib/gestao/use-capacidades";

/**
 * Controle das permissões efetivas nos testes de componente (vitest.setup.ts). O padrão é "tudo
 * permitido e pronto" — o contrato em que os testes de tela já existentes foram escritos. Testes de
 * permissão declaram o cenário (negadas, carregando, erro) ou pedem o hook real, que consulta
 * `/gestao/permissoes/minhas` pelo QueryClient do teste.
 */
interface Cenario {
  estado: EstadoDasCapacidades;
  negadas: ReadonlySet<string>;
  alcancaTodos?: boolean;
}

const PADRAO: Cenario = { estado: "pronto", negadas: new Set() };

let cenario: Cenario | "real" = PADRAO;
let recarregar: () => void = () => {};

export function definirCapacidadesDeTeste(opcoes: {
  estado?: EstadoDasCapacidades;
  negadas?: string[];
  alcancaTodos?: boolean;
  onRecarregar?: () => void;
}) {
  cenario = {
    estado: opcoes.estado ?? "pronto",
    negadas: new Set(opcoes.negadas ?? []),
    alcancaTodos: opcoes.alcancaTodos,
  };
  recarregar = opcoes.onRecarregar ?? (() => {});
}

/** O componente passa a usar o `useCapacidades` de produção. */
export function usarCapacidadesReais() {
  cenario = "real";
}

export function restaurarCapacidadesDeTeste() {
  cenario = PADRAO;
  recarregar = () => {};
}

export function cenarioDeCapacidades(): Cenario | "real" {
  return cenario;
}

export function capacidadesDoCenario(atual: Cenario): Capacidades {
  const pronto = atual.estado === "pronto";
  // Sem alcance declarado, espelha o recorte estrutural do backend: só ATENDENTE fica em "Meus".
  const alcancaTodos = atual.alcancaTodos ?? papelAtual() !== "ATENDENTE";
  return {
    estado: atual.estado,
    pode: (capacidade) => pronto && !atual.negadas.has(capacidade),
    alcancaTodos: pronto && alcancaTodos,
    recarregar: () => recarregar(),
  };
}

function papelAtual(): string | null {
  // Alguns testes substituem o módulo do auth-store por um seletor simples, sem `getState`.
  return typeof useAuthStore.getState === "function" ? useAuthStore.getState().papel : null;
}
