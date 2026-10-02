import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { estadoInicial, type EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { campanhaDeTeste, previaDeTeste, projecaoDeTeste, templateDeTeste } from "@/test/fabricas-de-campanha";
import { textosReais } from "@/test/textos-reais";

const estado = vi.hoisted(() => ({ papel: "ADMINISTRADOR", rota: vi.fn() }));
const api = vi.hoisted(() => ({
  listarTemplatesParaCampanha: vi.fn(),
  preverPublico: vi.fn(),
  listarExcluidos: vi.fn(),
  projetarEnvio: vi.fn(),
  criarCampanha: vi.fn(),
  atualizarRascunho: vi.fn(),
  iniciarCampanha: vi.fn(),
  enviarTeste: vi.fn(),
}));

vi.mock("@/lib/campanhas/api", () => api);
vi.mock("@/lib/tags/api", () => ({ listarTags: () => Promise.resolve([{ id: "tag1", nome: "Cliente VIP", cor: "#000", icone: null }]) }));
vi.mock("@/lib/lead/api", () => ({ listarEtapas: () => Promise.resolve([]) }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: estado.rota }) }));
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

import { AssistenteDeCampanha } from "./assistente-de-campanha";

const t = textosReais.campanhas;

function renderizar(inicial: EstadoDoAssistente, passoInicial = 0) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <AssistenteDeCampanha inicial={inicial} tetoDaInstancia={200} limiteMeta={0} passoInicial={passoInicial} />
    </QueryClientProvider>,
  );
}

function estadoPronto(sobrescritas: Partial<EstadoDoAssistente> = {}): EstadoDoAssistente {
  return {
    ...estadoInicial(100),
    rascunhoId: "c1",
    nome: "Retorno de orçamentos",
    template: { nome: "retorno_orcamento", idioma: "pt_BR", parametros: 1 },
    variaveis: [{ posicao: 1, campo: "PRIMEIRO_NOME", reserva: "cliente" }],
    ...sobrescritas,
  };
}

beforeEach(() => {
  Object.values(api).forEach((funcao) => funcao.mockReset());
  estado.rota.mockReset();
  estado.papel = "ADMINISTRADOR";
  api.listarTemplatesParaCampanha.mockResolvedValue([
    templateDeTeste(),
    templateDeTeste({ id: "t2", nome: "com_midia", elegivel: false, restricoes: ["CABECALHO_DE_MIDIA"] }),
  ]);
  api.preverPublico.mockResolvedValue(previaDeTeste());
  api.projetarEnvio.mockResolvedValue(projecaoDeTeste());
  api.criarCampanha.mockResolvedValue(campanhaDeTeste({ status: "RASCUNHO" }));
  api.atualizarRascunho.mockResolvedValue(campanhaDeTeste({ status: "RASCUNHO" }));
  api.iniciarCampanha.mockResolvedValue(campanhaDeTeste());
});

describe("passo 1: template", () => {
  it("não avança sem nome e template, mostrando o erro no campo", async () => {
    renderizar(estadoInicial(100));
    fireEvent.click(screen.getByRole("button", { name: t.assistente.avancar }));

    expect(await screen.findByText(t.passoTemplate.erroNome)).toBeInTheDocument();
    expect(screen.getByText(t.passoTemplate.erroTemplate)).toBeInTheDocument();
    expect(api.criarCampanha).not.toHaveBeenCalled();
  });

  it("mostra o template não suportado desabilitado e a prévia em bolha com o contato de exemplo", async () => {
    renderizar(estadoInicial(100));

    const bloqueado = await screen.findByRole("button", { name: /com_midia/ });
    expect(bloqueado).toBeDisabled();
    expect(within(bloqueado).getByText(t.passoTemplate.naoSuportado)).toBeInTheDocument();
    expect(screen.getByText(t.passoTemplate.restricoes.CABECALHO_DE_MIDIA)).toBeInTheDocument();

    fireEvent.click(await screen.findByRole("button", { name: /retorno_orcamento/ }));
    expect(await screen.findByText("Olá Marina, seu orçamento ficou pronto.")).toBeInTheDocument();
    expect(screen.getByText(t.passoTemplate.variaveis)).toBeInTheDocument();
  });

  it("com nome e template, salva o rascunho e passa ao público, que mostra a contagem real", async () => {
    renderizar(estadoInicial(100));
    fireEvent.change(screen.getByLabelText(t.passoTemplate.nome), { target: { value: "Retorno de outubro" } });
    fireEvent.click(await screen.findByRole("button", { name: /retorno_orcamento/ }));
    fireEvent.click(screen.getByRole("button", { name: t.assistente.avancar }));

    await waitFor(() => expect(api.criarCampanha).toHaveBeenCalledTimes(1));
    expect(api.criarCampanha.mock.calls[0][0]).toMatchObject({
      nome: "Retorno de outubro",
      templateNome: "retorno_orcamento",
      templateIdioma: "pt_BR",
      filtro: {},
    });
    expect(await screen.findByText(t.passoPublico.vaoReceber)).toBeInTheDocument();
    expect(await screen.findByText("120")).toBeInTheDocument();
    expect(screen.getByText(t.motivos.OPT_OUT)).toBeInTheDocument();
  });
});

describe("passo 2: público", () => {
  it("lista os contatos excluídos de um motivo para conferência", async () => {
    api.listarExcluidos.mockResolvedValue([{ leadId: "l1", nome: "Ana Souza", telefone: "5511999990001", motivo: "OPT_OUT" }]);
    renderizar(estadoPronto(), 1);

    const linha = (await screen.findByText(t.motivos.OPT_OUT)).closest("li") as HTMLElement;
    fireEvent.click(within(linha).getByRole("button", { name: t.passoPublico.verExcluidos }));
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
    expect(api.listarExcluidos).toHaveBeenCalledWith({}, "OPT_OUT");
  });

  it("avisa e bloqueia o avanço quando ninguém vai receber", async () => {
    api.preverPublico.mockResolvedValue(previaDeTeste({ total: 5, elegiveis: 0, excluidos: 5, excluidosPorMotivo: { OPT_OUT: 5 } }));
    renderizar(estadoPronto(), 1);

    expect(await screen.findByText(t.passoPublico.publicoVazio)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: t.assistente.avancar }));
    expect(api.atualizarRascunho).not.toHaveBeenCalled();
  });
});
