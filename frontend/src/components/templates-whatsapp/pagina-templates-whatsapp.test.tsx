import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/atendimento/api", () => ({
  listarTemplatesWhatsApp: () =>
    Promise.resolve([
      {
        nome: "retorno_orcamento",
        idioma: "pt_BR",
        categoria: "UTILIDADE",
        status: "PENDENTE",
        corpo: "Olá {{1}}, o orçamento ficou pronto.",
        quantidadeDeParametros: 1,
      },
    ]),
  criarTemplateWhatsApp: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    templatesWhatsApp: {
      titulo: "Templates do WhatsApp",
      descricao: "Crie modelos na Meta.",
      novo: "Novo template",
      carregando: "Carregando",
      vazio: "Vazio",
      erro: "Erro",
      dica: "Depois de aprovado aparece no composer.",
      avisoPendente: "Aguardando Meta",
      categorias: { UTILIDADE: "Utilidade", MARKETING: "Marketing", AUTENTICACAO: "Autenticação" },
      status: {
        APROVADO: "Aprovado",
        PENDENTE: "Pendente",
        REJEITADO: "Rejeitado",
        PAUSADO: "Pausado",
        DESCONHECIDO: "Desconhecido",
      },
      formulario: {
        criarTitulo: "Criar",
        nome: "Nome",
        nomeAjuda: "Ajuda nome",
        idioma: "Idioma",
        categoria: "Categoria",
        corpo: "Corpo",
        corpoAjuda: "Ajuda corpo",
        salvar: "Salvar",
        cancelar: "Cancelar",
        erro: "Erro ao salvar",
      },
    },
  }),
}));

import { PaginaTemplatesWhatsApp } from "./pagina-templates-whatsapp";

describe("pagina de templates WhatsApp", () => {
  it("lista o template devolvido pelo provedor, sem inventar modelo", async () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={cliente}>
        <PaginaTemplatesWhatsApp />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("retorno_orcamento")).toBeInTheDocument();
    expect(screen.getByText("Pendente")).toBeInTheDocument();
    expect(screen.getByText("Olá {{1}}, o orçamento ficou pronto.")).toBeInTheDocument();
  });
});
