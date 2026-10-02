import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { campanhaDeTeste, detalheDeTeste } from "@/test/fabricas-de-campanha";
import { textosReais } from "@/test/textos-reais";

const estado = vi.hoisted(() => ({ papel: "ADMINISTRADOR" }));
const api = vi.hoisted(() => ({
  pausarCampanha: vi.fn(),
  retomarCampanha: vi.fn(),
  cancelarCampanha: vi.fn(),
  alterarInterruptor: vi.fn(),
  alterarLimite: vi.fn(),
}));

vi.mock("@/lib/campanhas/api", () => api);
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (e: { papel: string; status: string }) => unknown) =>
    seletor({ papel: estado.papel, status: "autenticado" }),
}));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));

import { AjusteDeLimite } from "./ajuste-de-limite";
import { AlertaDePausa } from "./alerta-de-pausa";
import { ControlesDaCampanha } from "./controles-da-campanha";

function renderizar(ui: React.ReactNode) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={cliente}>{ui}</QueryClientProvider>);
}

const t = textosReais.campanhas;

describe("controles da campanha", () => {
  beforeEach(() => {
    Object.values(api).forEach((funcao) => funcao.mockReset());
    estado.papel = "ADMINISTRADOR";
  });

  it("administrador pausa a campanha em andamento", async () => {
    api.pausarCampanha.mockResolvedValue(campanhaDeTeste({ status: "PAUSADA" }));
    renderizar(<ControlesDaCampanha campanha={campanhaDeTeste()} />);

    fireEvent.click(screen.getByRole("button", { name: t.acoes.pausar }));
    await waitFor(() => expect(api.pausarCampanha).toHaveBeenCalledWith("c1"));
  });

  it.each(["GESTOR", "SUBGESTOR"])("%s vê os botões desabilitados e a explicação, e nada chama a API", (papel) => {
    estado.papel = papel;
    renderizar(<ControlesDaCampanha campanha={campanhaDeTeste()} />);

    const pausar = screen.getByRole("button", { name: t.acoes.pausar });
    expect(pausar).toBeDisabled();
    expect(pausar).toHaveAccessibleDescription(t.ajudaSomenteAdministrador);
    expect(screen.getByRole("button", { name: t.acoes.cancelar })).toBeDisabled();
    fireEvent.click(pausar);
    expect(api.pausarCampanha).not.toHaveBeenCalled();
  });

  it("cancelar exige confirmação e explica que não volta", async () => {
    api.cancelarCampanha.mockResolvedValue(campanhaDeTeste({ status: "CANCELADA" }));
    renderizar(<ControlesDaCampanha campanha={campanhaDeTeste()} />);

    fireEvent.click(screen.getByRole("button", { name: t.acoes.cancelar }));
    expect(await screen.findByText(t.acoes.cancelarDescricao)).toBeInTheDocument();
    expect(api.cancelarCampanha).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: t.acoes.cancelarConfirmar }));
    await waitFor(() => expect(api.cancelarCampanha).toHaveBeenCalledWith("c1"));
  });

  it("campanha pausada automaticamente oferece Retomar e mostra o alerta com motivo e o que fazer", async () => {
    const pausada = campanhaDeTeste({ status: "PAUSADA_AUTOMATICAMENTE", motivoDePausa: "TAXA_DE_FALHA:35", pausadaEm: "2026-10-02T10:00:00Z" });
    api.retomarCampanha.mockResolvedValue(campanhaDeTeste());
    renderizar(
      <>
        <AlertaDePausa campanha={pausada} />
        <ControlesDaCampanha campanha={pausada} />
      </>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("A taxa de falha chegou a 35%");
    expect(screen.getByText(t.detalhe.alertaPausa.oQueFazer)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: t.acoes.retomar })).toHaveAccessibleDescription(t.acoes.retomarAjuda);
    fireEvent.click(screen.getByRole("button", { name: t.acoes.retomar }));
    await waitFor(() => expect(api.retomarCampanha).toHaveBeenCalledWith("c1"));
  });

  it("campanha concluída não oferece nenhuma ação de controle", () => {
    renderizar(<ControlesDaCampanha campanha={campanhaDeTeste({ status: "CONCLUIDA" })} />);
    expect(screen.queryByRole("button", { name: t.acoes.pausar })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: t.acoes.cancelar })).not.toBeInTheDocument();
  });
});

describe("ajuste do limite diário", () => {
  beforeEach(() => {
    Object.values(api).forEach((funcao) => funcao.mockReset());
    estado.papel = "ADMINISTRADOR";
  });

  it("recusa valor acima do teto da instância sem chamar a API", () => {
    renderizar(<AjusteDeLimite detalhe={detalheDeTeste(campanhaDeTeste())} />);

    fireEvent.change(screen.getByLabelText(t.detalhe.limite.campo, { selector: "input[type=number]" }), { target: { value: "500" } });
    expect(screen.getByRole("alert")).toHaveTextContent("200");
    expect(screen.getByRole("button", { name: t.detalhe.limite.salvar })).toBeDisabled();
    expect(api.alterarLimite).not.toHaveBeenCalled();
  });

  it("salva um limite válido no teto", async () => {
    api.alterarLimite.mockResolvedValue(campanhaDeTeste({ limiteDiario: 150 }));
    renderizar(<AjusteDeLimite detalhe={detalheDeTeste(campanhaDeTeste())} />);

    fireEvent.change(screen.getByLabelText(t.detalhe.limite.campo, { selector: "input[type=number]" }), { target: { value: "150" } });
    fireEvent.click(screen.getByRole("button", { name: t.detalhe.limite.salvar }));
    await waitFor(() => expect(api.alterarLimite).toHaveBeenCalledWith("c1", 150, null));
  });

  it("gestor não salva: botão desabilitado com a explicação", () => {
    estado.papel = "GESTOR";
    renderizar(<AjusteDeLimite detalhe={detalheDeTeste(campanhaDeTeste())} />);
    expect(screen.getByRole("button", { name: t.detalhe.limite.salvar })).toBeDisabled();
    expect(screen.getByText(t.detalhe.limite.somenteAdministrador)).toBeInTheDocument();
  });
});
