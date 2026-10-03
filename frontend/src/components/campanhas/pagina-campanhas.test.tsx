import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";
import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";
import { campanhaDeTeste, listaDeTeste } from "@/test/fabricas-de-campanha";
import { textosReais } from "@/test/textos-reais";

const estado = vi.hoisted(() => ({ papel: "ADMINISTRADOR" }));
const api = vi.hoisted(() => ({ listarCampanhas: vi.fn() }));

vi.mock("@/lib/campanhas/api", () => api);
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (e: { papel: string; status: string }) => unknown) =>
    seletor({ papel: estado.papel, status: "autenticado" }),
}));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...resto}>
      {children}
    </a>
  ),
}));

import { PaginaCampanhas } from "./pagina-campanhas";

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false, retryDelay: 0 } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaCampanhas />
    </QueryClientProvider>,
  );
}

describe("pagina de campanhas", () => {
  it("mostra o esqueleto no formato da tela enquanto carrega", () => {
    api.listarCampanhas.mockReturnValue(new Promise(() => undefined));
    renderizar();
    expect(screen.getByRole("status", { name: textosReais.campanhas.lista.carregando })).toBeInTheDocument();
  });

  it("lista campanhas com status em texto, progresso acessível e os indicadores do topo", async () => {
    estado.papel = "ADMINISTRADOR";
    api.listarCampanhas.mockResolvedValue(listaDeTeste([campanhaDeTeste(), campanhaDeTeste({ id: "c2", nome: "Rascunho", status: "PAUSADA_AUTOMATICAMENTE" })]));
    renderizar();

    // Tabela (desktop) e cartões (celular) coexistem no DOM; o CSS esconde um deles conforme a largura.
    expect((await screen.findAllByText("Retorno de orçamentos")).length).toBe(2);
    expect(screen.getAllByText(textosReais.campanhas.status.EM_ANDAMENTO)).toHaveLength(2);
    expect(screen.getAllByText(textosReais.campanhas.status.PAUSADA_AUTOMATICAMENTE)).toHaveLength(2);
    expect(screen.getAllByRole("progressbar").length).toBeGreaterThan(1);
    expect(screen.getByText(textosReais.campanhas.indicadores.enviadasHoje, { exact: false })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: textosReais.campanhas.novaCampanha })).toHaveAttribute("href", "/campanhas/nova");
  });

  it("estado vazio explica e leva à primeira campanha (administrador)", async () => {
    estado.papel = "ADMINISTRADOR";
    api.listarCampanhas.mockResolvedValue(listaDeTeste([]));
    renderizar();

    expect(await screen.findByText(textosReais.campanhas.lista.vazioTitulo)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: textosReais.campanhas.lista.vazioAcao })).toBeInTheDocument();
  });

  it("gestor vê Nova campanha desabilitada e a razão, sem link para o assistente", async () => {
    estado.papel = "GESTOR";
    definirCapacidadesDeTeste({ negadas: ["campanhas.criar"] });
    api.listarCampanhas.mockResolvedValue(listaDeTeste([]));
    renderizar();

    const botao = await screen.findByRole("button", { name: textosReais.campanhas.novaCampanha });
    expect(botao).toBeDisabled();
    expect(botao).toHaveAccessibleDescription(textosReais.campanhas.criarSomenteAdministrador);
    expect(screen.queryByRole("link", { name: textosReais.campanhas.lista.vazioAcao })).not.toBeInTheDocument();
  });

  it("erro de carga mantém a tela e oferece nova tentativa", async () => {
    api.listarCampanhas.mockRejectedValue(new ErroDeApi(500, null, "falhou"));
    renderizar();

    expect(await screen.findByText(textosReais.campanhas.erroCarregar)).toBeInTheDocument();
    api.listarCampanhas.mockResolvedValue(listaDeTeste([campanhaDeTeste()]));
    screen.getByRole("button", { name: textosReais.estados.tentarNovamente }).click();
    await waitFor(() => expect(screen.getAllByText("Retorno de orçamentos").length).toBeGreaterThan(0));
  });

  it("funcionalidade desligada (404) vira estado de indisponível, não erro", async () => {
    api.listarCampanhas.mockRejectedValue(new ErroDeApi(404, null, "nao existe"));
    renderizar();
    expect(await screen.findByText(textosReais.campanhas.indisponivelTitulo)).toBeInTheDocument();
  });
});
