import { fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

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
vi.mock("@/lib/tags/api", () => ({ listarTags: () => Promise.resolve([]) }));
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

import { renderizarAssistenteNoPasso } from "./passos-testes-comuns";

const t = textosReais.campanhas;
const iniciar = () => screen.getByRole("button", { name: t.passoRevisao.iniciar });

async function prepararRevisao() {
  renderizarAssistenteNoPasso(3);
  await waitFor(() => expect(screen.getByText(/120 vão receber de 150/)).toBeInTheDocument());
}

function confirmar(total: string) {
  fireEvent.click(screen.getByRole("checkbox", { name: t.passoRevisao.consentimento }));
  fireEvent.change(screen.getByLabelText(t.passoRevisao.digiteTotalRotulo), { target: { value: total } });
}

beforeEach(() => {
  Object.values(api).forEach((funcao) => funcao.mockReset());
  estado.rota.mockReset();
  estado.papel = "ADMINISTRADOR";
  api.listarTemplatesParaCampanha.mockResolvedValue([templateDeTeste()]);
  api.preverPublico.mockResolvedValue(previaDeTeste());
  api.projetarEnvio.mockResolvedValue(projecaoDeTeste());
  api.atualizarRascunho.mockResolvedValue(campanhaDeTeste({ status: "RASCUNHO" }));
  api.iniciarCampanha.mockResolvedValue(campanhaDeTeste());
});

describe("passo 4: revisão e confirmação digitada", () => {
  it("só habilita Iniciar com consentimento e o número exato de destinatários digitado", async () => {
    await prepararRevisao();
    expect(iniciar()).toBeDisabled();

    fireEvent.click(screen.getByRole("checkbox", { name: t.passoRevisao.consentimento }));
    expect(iniciar()).toBeDisabled();

    const confirmacao = screen.getByLabelText(t.passoRevisao.digiteTotalRotulo);
    fireEvent.change(confirmacao, { target: { value: "12" } });
    expect(iniciar()).toBeDisabled();
    fireEvent.change(confirmacao, { target: { value: "120" } });
    expect(iniciar()).toBeEnabled();
  });

  it("ao iniciar, grava o rascunho, inicia a campanha e abre o detalhe", async () => {
    await prepararRevisao();
    confirmar("120");
    fireEvent.click(iniciar());

    await waitFor(() => expect(api.iniciarCampanha).toHaveBeenCalledWith("c1"));
    expect(api.atualizarRascunho).toHaveBeenCalled();
    expect(estado.rota).toHaveBeenCalledWith("/campanhas/c1");
  });

  it("mostra o erro do backend ao iniciar e mantém o assistente aberto", async () => {
    api.iniciarCampanha.mockRejectedValue(new Error("A instância atingiu o teto diário."));
    await prepararRevisao();
    confirmar("120");
    fireEvent.click(iniciar());

    expect(await screen.findByText("A instância atingiu o teto diário.")).toBeInTheDocument();
    expect(estado.rota).not.toHaveBeenCalled();
  });

  it("envia o teste ao contato só com telefone e autorização marcada", async () => {
    api.enviarTeste.mockResolvedValue({ leadId: "l1", mensagemId: "m1", enviadoEm: "2026-10-02T10:00:00Z", corpoRenderizado: "Olá" });
    await prepararRevisao();
    const enviar = screen.getByRole("button", { name: t.passoRevisao.testeEnviar });
    expect(enviar).toBeDisabled();

    fireEvent.change(screen.getByLabelText(t.passoRevisao.testeTelefone), { target: { value: "5511999990000" } });
    expect(enviar).toBeDisabled();
    fireEvent.click(screen.getByRole("checkbox", { name: t.passoRevisao.testeAutorizou }));
    fireEvent.click(enviar);

    await waitFor(() => expect(api.enviarTeste).toHaveBeenCalledWith("c1", "5511999990000", true));
    expect(await screen.findByText(t.passoRevisao.testeEnviado)).toBeInTheDocument();
  });

  it("sem público elegível, Iniciar fica desabilitado e a pendência é dita", async () => {
    api.preverPublico.mockResolvedValue(previaDeTeste({ total: 3, elegiveis: 0, excluidos: 3, excluidosPorMotivo: { OPT_OUT: 3 } }));
    renderizarAssistenteNoPasso(3);
    expect(await screen.findByText(t.passoRevisao.pendenciaPublico)).toBeInTheDocument();
    expect(iniciar()).toBeDisabled();
  });
});
