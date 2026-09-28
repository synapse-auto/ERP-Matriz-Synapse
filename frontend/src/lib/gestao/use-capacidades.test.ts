import { describe, expect, it, vi } from "vitest";

import type { MinhasPermissoes } from "./types";
import { capacidadesDe } from "./use-capacidades";

function minhas(capacidades: MinhasPermissoes["capacidades"]): MinhasPermissoes {
  return {
    usuarioId: "u",
    papel: "ATENDENTE",
    revisao: 3,
    acessaGestao: false,
    editaPerfis: false,
    editaExcecoes: false,
    capacidades,
  };
}

describe("capacidadesDe", () => {
  it("sem resposta do backend, nada é permitido (carregando)", () => {
    const capacidades = capacidadesDe(undefined, false, vi.fn());
    expect(capacidades.estado).toBe("carregando");
    expect(capacidades.pode("templates.ver")).toBe(false);
    expect(capacidades.alcancaTodos).toBe(false);
  });

  it("com falha e sem resposta anterior, fica em erro e continua negando", () => {
    const recarregar = vi.fn();
    const capacidades = capacidadesDe(undefined, true, recarregar);
    expect(capacidades.estado).toBe("erro");
    expect(capacidades.pode("templates.criar")).toBe(false);
    capacidades.recarregar();
    expect(recarregar).toHaveBeenCalled();
  });

  it("usa exatamente o permitido calculado pelo backend e nega id ausente", () => {
    const capacidades = capacidadesDe(
      minhas({
        "templates.ver": { permitido: true, motivo: "PERMITIDO", alcance: null },
        "templates.criar": { permitido: false, motivo: "DESLIGADO", alcance: null },
      }),
      false,
      vi.fn(),
    );
    expect(capacidades.estado).toBe("pronto");
    expect(capacidades.pode("templates.ver")).toBe(true);
    expect(capacidades.pode("templates.criar")).toBe(false);
    expect(capacidades.pode("templates.editar")).toBe(false);
  });

  it("uma revalidação que falhe mantém a última resposta conhecida", () => {
    const capacidades = capacidadesDe(
      minhas({ "templates.ver": { permitido: true, motivo: "PERMITIDO", alcance: null } }),
      true,
      vi.fn(),
    );
    expect(capacidades.estado).toBe("pronto");
    expect(capacidades.pode("templates.ver")).toBe(true);
  });

  it("alcance vem do recorte estrutural de atendimentos.ver", () => {
    const todos = capacidadesDe(
      minhas({ "atendimentos.ver": { permitido: true, motivo: "PERMITIDO", alcance: "TODOS" } }),
      false,
      vi.fn(),
    );
    const meus = capacidadesDe(
      minhas({ "atendimentos.ver": { permitido: true, motivo: "PERMITIDO", alcance: "MEUS" } }),
      false,
      vi.fn(),
    );
    expect(todos.alcancaTodos).toBe(true);
    expect(meus.alcancaTodos).toBe(false);
  });
});
