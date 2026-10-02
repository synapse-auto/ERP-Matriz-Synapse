import { fireEvent, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { campanhaDeTeste, previaDeTeste, projecaoDeTeste, templateDeTeste } from "@/test/fabricas-de-campanha";
import { textosReais } from "@/test/textos-reais";

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
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...resto}>
      {children}
    </a>
  ),
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (e: { papel: string; status: string }) => unknown) =>
    seletor({ papel: "ADMINISTRADOR", status: "autenticado" }),
}));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => textosReais }));

import { renderizarAssistenteNoPasso } from "./passos-testes-comuns";

const t = textosReais.campanhas;
const campoLimite = () => screen.getByLabelText(t.passoRitmo.limiteDiario, { selector: "input[type=number]" });

beforeEach(() => {
  Object.values(api).forEach((funcao) => funcao.mockReset());
  api.listarTemplatesParaCampanha.mockResolvedValue([templateDeTeste()]);
  api.preverPublico.mockResolvedValue(previaDeTeste());
  api.projetarEnvio.mockResolvedValue(projecaoDeTeste());
  api.atualizarRascunho.mockResolvedValue(campanhaDeTeste({ status: "RASCUNHO" }));
});

describe("passo 3: ritmo e agenda", () => {
  it("bloqueia limite acima do teto da instância e explica o teto", async () => {
    renderizarAssistenteNoPasso(2);
    fireEvent.change(campoLimite(), { target: { value: "500" } });

    expect(await screen.findByText(/não pode passar de 200/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: t.assistente.avancar }));
    expect(api.atualizarRascunho).not.toHaveBeenCalled();
  });

  it("os atalhos nunca passam do teto e o último é o próprio teto", () => {
    renderizarAssistenteNoPasso(2);
    const atalhos = screen.getByRole("group", { name: t.passoRitmo.atalhos });
    expect(atalhos).toHaveTextContent(/50.*100.*Teto \(200\)/);
    expect(atalhos).not.toHaveTextContent("500");
  });

  it("avisa, sem bloquear, quando o limite passa do limite informado da Meta", async () => {
    renderizarAssistenteNoPasso(2);
    fireEvent.change(campoLimite(), { target: { value: "180" } });
    expect(await screen.findByText(/acima do limite informado da Meta \(150\)/)).toBeInTheDocument();
    expect(campoLimite()).not.toBeInvalid();
  });

  it("mostra a estimativa de término e o mini-calendário com as mensagens por dia", async () => {
    renderizarAssistenteNoPasso(2);
    expect(await screen.findByText(/Termina em 06\/10\/2026/)).toBeInTheDocument();
    expect(screen.getByRole("region", { name: t.passoRitmo.calendarioRotulo })).toHaveTextContent("100 msg");
  });

  it("recusa janela com fim antes do início", async () => {
    renderizarAssistenteNoPasso(2, { janelaInicio: "18:00", janelaFim: "09:00" });
    expect(await screen.findByText(t.passoRitmo.janelaInvalida)).toBeInTheDocument();
  });
});
