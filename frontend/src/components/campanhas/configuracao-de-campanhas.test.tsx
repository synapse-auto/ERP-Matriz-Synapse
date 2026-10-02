import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { textosReais } from "@/test/textos-reais";

const estado = vi.hoisted(() => ({ papel: "ADMINISTRADOR" }));
const api = vi.hoisted(() => ({ obterConfiguracaoDeCampanhas: vi.fn(), atualizarConfiguracaoDeCampanhas: vi.fn() }));

vi.mock("@/lib/campanhas/api", () => api);
vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...resto}>
      {children}
    </a>
  ),
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (e: { papel: string; status: string }) => unknown) =>
    seletor({ papel: estado.papel, status: "autenticado" }),
}));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));

import { PaginaConfiguracaoDeCampanhas } from "./pagina-configuracao-de-campanhas";

const t = textosReais.campanhas.configuracaoDaInstancia;
const configuracao = {
  envioHabilitado: true, tetoDiarioDaInstancia: 200, limiteDiarioPadrao: 100, limiteMetaInformado: 0,
  limiarDeFalhaPorCento: 20, janelaDeEnvios: 50, minimoDeAmostra: 10, conferenciaAposMinutos: 30, respondeuJanelaDias: 7,
};

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaConfiguracaoDeCampanhas />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  Object.values(api).forEach((funcao) => funcao.mockReset());
  estado.papel = "ADMINISTRADOR";
  api.obterConfiguracaoDeCampanhas.mockResolvedValue(configuracao);
  api.atualizarConfiguracaoDeCampanhas.mockResolvedValue(configuracao);
});

describe("configurações de campanhas da instância", () => {
  it("administrador altera o teto e salva só valores dentro da faixa", async () => {
    renderizar();
    const teto = await screen.findByLabelText(t.teto);
    fireEvent.change(teto, { target: { value: "0" } });
    expect(screen.getByRole("button", { name: t.salvar })).toBeDisabled();

    fireEvent.change(teto, { target: { value: "300" } });
    fireEvent.click(screen.getByRole("button", { name: t.salvar }));
    await waitFor(() => expect(api.atualizarConfiguracaoDeCampanhas).toHaveBeenCalled());
    expect(api.atualizarConfiguracaoDeCampanhas.mock.calls[0][0]).toMatchObject({ tetoDiarioDaInstancia: 300, limiarDeFalhaPorCento: 20 });
  });

  it("limite padrão acima do teto é inválido", async () => {
    renderizar();
    fireEvent.change(await screen.findByLabelText(t.limitePadrao), { target: { value: "250" } });
    expect(screen.getByLabelText(t.limitePadrao)).toBeInvalid();
    expect(screen.getByRole("button", { name: t.salvar })).toBeDisabled();
  });

  it("gestor só consulta: campos e botão desabilitados, com a explicação", async () => {
    estado.papel = "GESTOR";
    renderizar();
    expect(await screen.findByLabelText(t.teto)).toBeDisabled();
    expect(screen.getByRole("button", { name: t.salvar })).toBeDisabled();
    expect(screen.getByText(t.somenteAdministrador)).toBeInTheDocument();
  });
});
