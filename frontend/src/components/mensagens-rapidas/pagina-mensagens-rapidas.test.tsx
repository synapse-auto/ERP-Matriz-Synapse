import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const removerMutate = vi.fn();

const MENSAGENS = [
  { id: "m1", atendenteId: "u1", atendenteNome: "Ana Beatriz", palavraChave: "orcamento", conteudo: "Segue o orçamento.", tipoMidia: null },
  { id: "m2", atendenteId: "u1", atendenteNome: "Ana Beatriz", palavraChave: "pix", conteudo: "Segue a chave PIX.", tipoMidia: null },
  { id: "m3", atendenteId: "u2", atendenteNome: "Bruno Costa", palavraChave: "prazo", conteudo: "O prazo médio é de 15 dias.", tipoMidia: null },
];

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (selector: (s: { papel: string }) => unknown) => selector({ papel: "GESTOR" }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    mensagensRapidas: {
      titulo: "Mensagens Rápidas",
      descricao: "Atalhos pessoais para respostas usadas com frequência.",
      nova: "Nova mensagem rápida",
      carregando: "Carregando mensagens...",
      vazio: "Nenhuma mensagem rápida cadastrada.",
      erro: "Não foi possível carregar as mensagens rápidas.",
      editar: "Editar",
      remover: "Remover",
      dica: "Digite a palavra-chave no composer.",
      formulario: {
        criarTitulo: "Criar mensagem rápida",
        editarTitulo: "Editar mensagem rápida",
        chave: "Palavra-chave",
        conteudo: "Mensagem",
        salvar: "Salvar",
        cancelar: "Cancelar",
        erro: "Não foi possível salvar.",
      },
    },
  }),
}));

vi.mock("@/lib/suporte/api", () => ({
  listarMensagensRapidas: () => Promise.resolve(MENSAGENS),
  removerMensagemRapida: (...args: unknown[]) => {
    removerMutate(...args);
    return Promise.resolve();
  },
  criarMensagemRapida: vi.fn(),
  editarMensagemRapida: vi.fn(),
}));

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { PaginaMensagensRapidas } from "./pagina-mensagens-rapidas";

function renderComQuery() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaMensagensRapidas />
    </QueryClientProvider>,
  );
}

describe("pagina de mensagens rapidas", () => {
  it("agrupa as mensagens por atendente, com contagem", async () => {
    renderComQuery();

    expect(await screen.findByText("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByText("Bruno Costa")).toBeInTheDocument();
    expect(screen.getByText("/orcamento")).toBeInTheDocument();
    expect(screen.getByText("/pix")).toBeInTheDocument();
    expect(screen.getByText("/prazo")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("remove uma mensagem rápida", async () => {
    renderComQuery();

    await screen.findByText("/orcamento");
    fireEvent.click(screen.getByRole("button", { name: "Remover /orcamento" }));

    await waitFor(() => expect(removerMutate).toHaveBeenCalledWith("m1", expect.anything()));
  });
});
