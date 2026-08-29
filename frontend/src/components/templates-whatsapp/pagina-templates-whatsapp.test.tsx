import { fireEvent, render, screen, within } from "@testing-library/react";
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
        corpoAjuda: "Sem cabeçalho nesta versão.",
        variaveisDetectadas: "Variáveis sequenciais: {lista}",
        variavelAusente: "Falta {marcador}.",
        variavelInvalida: "O índice {marcador} é inválido.",
        salvar: "Salvar",
        cancelar: "Cancelar",
        erro: "Erro ao salvar",
      },
    },
  }),
}));

import { criarTemplateWhatsApp } from "@/lib/atendimento/api";
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

  it("lista todas as variaveis presentes no corpo", async () => {
    const formulario = await abrirFormulario();
    fireEvent.change(within(formulario).getByLabelText("Corpo"), {
      target: { value: "Olá {{1}}, {{2}}, {{3}} e {{4}}." },
    });

    expect(
      within(formulario).getByText(/Variáveis sequenciais: \{\{1\}\}, \{\{2\}\}, \{\{3\}\}, \{\{4\}\}/),
    ).toBeInTheDocument();
  });

  it("bloqueia envio quando falta um indice", async () => {
    const formulario = await abrirFormulario();
    fireEvent.change(within(formulario).getByLabelText("Nome"), { target: { value: "retorno" } });
    fireEvent.change(within(formulario).getByLabelText("Corpo"), {
      target: { value: "Olá {{1}} e {{3}}" },
    });

    expect(within(formulario).getByRole("alert")).toHaveTextContent("Falta {{2}}.");
    expect(within(formulario).getByRole("button", { name: "Salvar" })).toBeDisabled();
    fireEvent.click(within(formulario).getByRole("button", { name: "Salvar" }));
    expect(criarTemplateWhatsApp).not.toHaveBeenCalled();
  });
});

async function abrirFormulario() {
  renderizar();
  await screen.findByText("retorno_orcamento");
  fireEvent.click(screen.getByRole("button", { name: "Novo template" }));
  return screen.findByRole("dialog");
}

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={cliente}>
      <PaginaTemplatesWhatsApp />
    </QueryClientProvider>,
  );
}
