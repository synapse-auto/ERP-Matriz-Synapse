import { describe, expect, it } from "vitest";

import type { Tema, Textos } from "@/lib/config/schema";

import { montarMetadata } from "./metadata";

const textos = { app: { nome: "Synapse CRM", subtitulo: "Atendimento" } } as Textos;
const tema = { logoUrl: "/logos/instancia.svg" } as Tema;

describe("metadata da aplicação", () => {
  it("usa a logo da instância como favicon sem alterar a identidade do login", () => {
    expect(montarMetadata(textos, tema)).toMatchObject({
      title: "Synapse CRM",
      description: "Atendimento",
      icons: { icon: "/logos/instancia.svg" },
    });
  });

  it("não publica ícone quando a instância não configurou logo", () => {
    expect(montarMetadata(textos, { logoUrl: null } as Tema)).not.toHaveProperty("icons");
  });
});
