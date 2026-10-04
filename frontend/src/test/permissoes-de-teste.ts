import type { MinhasPermissoes, Papel } from "@/lib/gestao/types";

const LEITURAS_DE_TODOS = ["templates.ver", "mensagens_rapidas.usar", "mensagens_programadas.ver", "lembretes.ver"];
const LEITURAS_DA_GESTAO = ["dashboard.ver", "automacao.ver", "campanhas.ver", "equipe.ver"];

/**
 * Resposta de `/gestao/permissoes/minhas` com o padrão de cada papel (docs/47 §2.1), restrita às
 * leituras que decidem o menu. Serve para semear o QueryClient de testes do shell.
 */
export function permissoesEfetivasDeTeste(papel: Papel, negadas: string[] = []): MinhasPermissoes {
  const gestao = ["SUBGESTOR", "GESTOR", "ADMINISTRADOR"].includes(papel);
  const ids = gestao ? [...LEITURAS_DE_TODOS, ...LEITURAS_DA_GESTAO] : LEITURAS_DE_TODOS;
  return {
    usuarioId: "usuario-de-teste",
    papel,
    revisao: 1,
    acessaGestao: gestao,
    editaPerfis: papel === "GESTOR" || papel === "ADMINISTRADOR",
    editaExcecoes: papel === "GESTOR" || papel === "ADMINISTRADOR",
    capacidades: Object.fromEntries(
      ids.map((id) => [id, { permitido: !negadas.includes(id), motivo: "PERMITIDO" as const, alcance: null }]),
    ),
  };
}
