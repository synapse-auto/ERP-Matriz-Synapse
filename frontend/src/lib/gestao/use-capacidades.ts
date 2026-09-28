"use client";

import { useAuthStore } from "@/lib/auth/auth-store";

import type { MinhasPermissoes } from "./types";
import { useMinhasPermissoes } from "./use-gestao";

export type EstadoDasCapacidades = "carregando" | "erro" | "pronto";

export interface Capacidades {
  estado: EstadoDasCapacidades;
  /** Permissão efetiva calculada pelo backend (perfil, exceção, teto do papel e flags). */
  pode: (capacidade: string) => boolean;
  /** Recorte estrutural de `atendimentos.ver` (RN-CRM-01): `true` quando alcança todos os leads. */
  alcancaTodos: boolean;
  recarregar: () => void;
}

/**
 * Único ponto em que a tela pergunta "este usuário pode?" (Gestão, docs/47). A resposta vem de
 * `/gestao/permissoes/minhas`; o frontend não recalcula concessão nem usa o papel como atalho.
 *
 * Estado seguro: sem resposta (carregando ou erro), `pode` é sempre `false` — nenhuma ação
 * privilegiada aparece e some depois. Com uma resposta já obtida, uma revalidação que falhe mantém
 * a última conhecida; o backend continua negando o que foi revogado.
 */
export function useCapacidades(): Capacidades {
  const sessaoPronta = useAuthStore(
    (estado) => estado.status === "autenticado" && !estado.precisaTrocarSenha,
  );
  const consulta = useMinhasPermissoes(sessaoPronta);
  return capacidadesDe(consulta.data, consulta.isError, () => void consulta.refetch());
}

export function capacidadesDe(
  minhas: MinhasPermissoes | undefined,
  falhou: boolean,
  recarregar: () => void,
): Capacidades {
  if (!minhas) {
    return { estado: falhou ? "erro" : "carregando", pode: () => false, alcancaTodos: false, recarregar };
  }
  return {
    estado: "pronto",
    pode: (capacidade) => minhas.capacidades[capacidade]?.permitido === true,
    alcancaTodos: minhas.capacidades["atendimentos.ver"]?.alcance === "TODOS",
    recarregar,
  };
}
