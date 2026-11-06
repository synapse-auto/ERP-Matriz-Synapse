import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ColetaDarwin } from "./coleta-darwin";
import { disponibilidadeDarwin, estadoColetaDarwin, tentarColetaDarwin } from "@/lib/lead/darwin";

vi.mock("@/lib/lead/darwin", () => ({ disponibilidadeDarwin: vi.fn(), estadoColetaDarwin: vi.fn(), tentarColetaDarwin: vi.fn() }));
vi.mock("@/lib/auth/auth-store", () => ({ useAuthStore: (fn: (s: { usuarioId: string }) => unknown) => fn({ usuarioId: "usuario-a" }) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => ({ dadosCliente: {
  consultar: "Atualizar dados cadastrais", consultando: "Consultando cadastro…", identificacaoPendente: "Identificação pendente",
  revisao: "Revisar cadastro", atualizado: "Cadastro consultado", naoEncontrado: "Paciente não encontrado", falhaColeta: "Tente novamente mais tarde",
} }) }));
const disponivel = { habilitada: true, estado: "DISPONIVEL" as const };
function montar(id = "lead-a", permitido = true) {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const tela = render(<QueryClientProvider client={cache}><ColetaDarwin key={id} leadId={id} permitido={permitido} /></QueryClientProvider>);
  return { ...tela, cache };
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(disponibilidadeDarwin).mockResolvedValue(disponivel);
  vi.mocked(estadoColetaDarwin).mockResolvedValue({ estado: "ATUALIZADO" });
  vi.mocked(tentarColetaDarwin).mockResolvedValue(undefined);
});
describe("coleta cadastral real não exige digitar CPF", () => {
  it("exibe resultado, não dispara mutation ao renderizar e solicita uma tentativa pelo botão", async () => {
    const { cache } = montar();
    await screen.findByText("Cadastro consultado");
    expect(tentarColetaDarwin).not.toHaveBeenCalled();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    const invalidar = vi.spyOn(cache, "invalidateQueries");
    fireEvent.click(screen.getByRole("button", { name: "Atualizar dados cadastrais" }));
    await waitFor(() => expect(tentarColetaDarwin).toHaveBeenCalledExactlyOnceWith("lead-a"));
    await waitFor(() => expect(invalidar).toHaveBeenCalledWith({ queryKey: ["lead", "lead-a"], exact: true }));
  });
  it.each(["IDENTIFICACAO_PENDENTE", "REVISAO_NECESSARIA", "NAO_ENCONTRADO", "INDISPONIVEL", "PROCESSANDO"])("mostra estado %s sem apagar os campos locais", async estado => {
    vi.mocked(estadoColetaDarwin).mockResolvedValue({ estado }); montar();
    await screen.findByRole("status");
    if (["PROCESSANDO", "IDENTIFICACAO_PENDENTE"].includes(estado)) expect(screen.getByRole("button")).toBeDisabled();
    expect(tentarColetaDarwin).not.toHaveBeenCalled();
  });
  it("OFF e acesso negado não consultam estado nem enviam pedido", async () => {
    vi.mocked(disponibilidadeDarwin).mockResolvedValue({ habilitada: false, estado: "DESABILITADA" });
    const tela = montar();
    await waitFor(() => expect(disponibilidadeDarwin).toHaveBeenCalledTimes(1));
    expect(estadoColetaDarwin).not.toHaveBeenCalled(); tela.unmount();
    montar("outro", false);
    expect(disponibilidadeDarwin).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
  it("troca rápida de lead não mostra resposta tardia do anterior", async () => {
    let terminar!: (value: { estado: string }) => void;
    vi.mocked(estadoColetaDarwin).mockImplementation(id => id === "lead-a" ? new Promise(r => { terminar = r; }) : Promise.resolve({ estado: "IDENTIFICACAO_PENDENTE" }));
    const tela = montar(); await waitFor(() => expect(terminar).toBeDefined());
    tela.rerender(<QueryClientProvider client={tela.cache}><ColetaDarwin key="lead-b" leadId="lead-b" permitido /></QueryClientProvider>);
    await screen.findByText("Identificação pendente");
    await act(async () => { terminar({ estado: "ATUALIZADO" }); });
    expect(screen.queryByText("Cadastro consultado")).not.toBeInTheDocument();
  });
  it("erro na tentativa é legível e permite continuar usando a ficha", async () => {
    vi.mocked(tentarColetaDarwin).mockRejectedValue(new Error("falha sintética")); montar();
    fireEvent.click(await screen.findByRole("button"));
    await screen.findByText("Tente novamente mais tarde");
    expect(screen.getByRole("button")).toBeEnabled();
  });
});
