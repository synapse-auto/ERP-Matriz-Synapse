import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

import "@testing-library/jest-dom/vitest";

import { restaurarCapacidadesDeTeste } from "@/test/capacidades-de-teste";

// Permissões efetivas controladas pelo teste (src/test/capacidades-de-teste.ts). `usarCapacidadesReais()`
// devolve o hook de produção para os testes que exercitam a consulta ao backend.
vi.mock("@/lib/gestao/use-capacidades", async (importOriginal) => {
  const real = await importOriginal<typeof import("@/lib/gestao/use-capacidades")>();
  const controle = await import("@/test/capacidades-de-teste");
  return {
    ...real,
    useCapacidades: () => {
      const cenario = controle.cenarioDeCapacidades();
      return cenario === "real" ? real.useCapacidades() : controle.capacidadesDoCenario(cenario);
    },
  };
});

// Sem `globals: true` no vitest.config.ts, o auto-cleanup do Testing Library não se registra
// sozinho — sem isto, o DOM de um teste vaza para o próximo dentro do mesmo arquivo.
afterEach(() => {
  cleanup();
  restaurarCapacidadesDeTeste();
});
