import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { campanhaDeTeste, detalheDeTeste } from "@/test/fabricas-de-campanha";
import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";
import { textosReais } from "@/test/textos-reais";

const estado = vi.hoisted(() => ({ papel: "ADMINISTRADOR" }));
const api = vi.hoisted(() => ({
  obterCampanha: vi.fn(),
  listarDestinatarios: vi.fn(),
  listarConferencia: vi.fn(),
  marcarComoConferido: vi.fn(),
  baixarDestinatariosCsv: vi.fn(),
}));

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
vi.mock("recharts", async (importOriginal) => {
  const real = await importOriginal<typeof import("recharts")>();
  return { ...real, ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div> };
});

import { PaginaDetalheDaCampanha } from "./pagina-detalhe-da-campanha";

const t = textosReais.campanhas;
const linhaDeTeste = {
  id: "d1", leadId: "l1", nome: "Ana Souza", telefone: "5511999990001", status: "FALHA" as const,
  motivo: "FALHA_NO_PROVEDOR" as const, codigoDeErro: 131026, enviadoEm: null, entregueEm: null, lidoEm: null,
  respondeuEm: null, conferenciaEm: null,
};

function renderizar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <PaginaDetalheDaCampanha id="c1" />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  Object.values(api).forEach((funcao) => funcao.mockReset());
  estado.papel = "ADMINISTRADOR";
  api.obterCampanha.mockResolvedValue(detalheDeTeste(campanhaDeTeste()));
  api.listarDestinatarios.mockResolvedValue({ itens: [linhaDeTeste], pagina: 0, tamanho: 10, total: 1 });
  api.listarConferencia.mockResolvedValue({ itens: [linhaDeTeste], pagina: 0, tamanho: 10, total: 1 });
});

describe("detalhe da campanha", () => {
  it("mostra o funil acumulado: na fila soma pendentes e enfileirados ainda não enviados", async () => {
    renderizar();
    const funil = await screen.findByRole("region", { name: t.detalhe.funil.rotulo });
    // pendentes 100 + (enfileirados 100 - enviados 80) = 120 na fila; 80 enviadas; 60 entregues; 30 lidas; 10 responderam.
    const etapas = within(funil).getAllByRole("listitem");
    expect(etapas[0]).toHaveTextContent("120");
    expect(etapas[1]).toHaveTextContent("80");
    expect(etapas[2]).toHaveTextContent("60");
    expect(etapas[3]).toHaveTextContent("30");
    expect(etapas[4]).toHaveTextContent("10");
  });

  it("pausa automática destaca o alerta antes das abas", async () => {
    api.obterCampanha.mockResolvedValue(
      detalheDeTeste(campanhaDeTeste({ status: "PAUSADA_AUTOMATICAMENTE", motivoDePausa: "ERRO_DA_META:131048" })),
    );
    renderizar();
    expect(await screen.findByRole("alert")).toHaveTextContent("A Meta devolveu o código 131048");
  });

  it("filtra destinatários por status e motivo via API", async () => {
    renderizar();
    fireEvent.click(await screen.findByRole("tab", { name: t.detalhe.abas.destinatarios }));
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
    expect(screen.getByText(t.motivos.FALHA_NO_PROVEDOR)).toBeInTheDocument();
    expect(screen.getByText("131026")).toBeInTheDocument();
    await waitFor(() => expect(api.listarDestinatarios).toHaveBeenCalledWith("c1", { status: null, motivo: null }, 0, 10));
  });

  it("exporta o CSV autenticado como arquivo", async () => {
    const criar = vi.fn(() => "blob:csv");
    Object.assign(URL, { createObjectURL: criar, revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    api.baixarDestinatariosCsv.mockResolvedValue({ blob: new Blob(["a"]), nome: "campanha-c1.csv" });
    renderizar();
    fireEvent.click(await screen.findByRole("tab", { name: t.detalhe.abas.destinatarios }));
    fireEvent.click(await screen.findByRole("button", { name: t.detalhe.destinatarios.exportar }));

    await waitFor(() => expect(api.baixarDestinatariosCsv).toHaveBeenCalledWith("c1"));
    await waitFor(() => expect(criar).toHaveBeenCalled());
  });

  it("conferência manual: administrador marca como conferido, sem reenviar", async () => {
    api.marcarComoConferido.mockResolvedValue(undefined);
    renderizar();
    fireEvent.click(await screen.findByRole("tab", { name: new RegExp(t.detalhe.abas.conferencia) }));
    fireEvent.click(await screen.findByRole("button", { name: t.detalhe.conferencia.marcar }));
    await waitFor(() => expect(api.marcarComoConferido).toHaveBeenCalledWith("c1", "d1"));
  });

  it("conferência manual: gestor vê o botão desabilitado e a explicação", async () => {
    estado.papel = "GESTOR";
    definirCapacidadesDeTeste({ negadas: ["campanhas.conferir"] });
    renderizar();
    fireEvent.click(await screen.findByRole("tab", { name: new RegExp(t.detalhe.abas.conferencia) }));
    expect(await screen.findByRole("button", { name: t.detalhe.conferencia.marcar })).toBeDisabled();
    expect(screen.getByText(t.detalhe.conferencia.somenteAdministrador)).toBeInTheDocument();
  });
});
