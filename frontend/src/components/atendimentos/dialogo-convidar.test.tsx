import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

type Destino = { id: string; nome: string; papel?: "ATENDENTE" | "SUBGESTOR" };

const estado = vi.hoisted(() => ({
  destinos: [] as Destino[],
  falharLista: false,
  respostaConvite: null as null | (() => Promise<unknown>),
  chamadas: [] as Array<{ url: string; metodo: string; corpo?: string }>,
}));

// A rede é observada no ponto mais baixo do cliente: assim o teste prova a URL que sai, e não
// apenas que uma função com nome de convite foi chamada.
vi.mock("@/lib/api/http-client", () => ({
  apiFetch: vi.fn((url: string, init?: RequestInit) => {
    estado.chamadas.push({ url, metodo: init?.method ?? "GET", corpo: init?.body as string | undefined });
    if (url.endsWith("/destinos-de-transferencia")) {
      return estado.falharLista ? Promise.reject(new Error("falha")) : Promise.resolve(estado.destinos);
    }
    if (url.endsWith("/convidar")) {
      return estado.respostaConvite ? estado.respostaConvite() : Promise.resolve({ jaExistia: false });
    }
    return Promise.reject(new Error(`chamada inesperada ${url}`));
  }),
}));
vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estadoAtual: { usuarioId: string }) => unknown) => seletor({ usuarioId: "eu-1" }),
}));
vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      cabecalho: {
        outros: "Outros",
        voltar: "Voltar",
        semAtendente: "Sem atendente",
        convidarTitulo: "Convidar para atendimento",
        convidarCarregando: "Carregando",
        convidarVazio: "Nenhum atendente",
        convidarErro: "Erro ao convidar",
        convidarResponsavel: "Responsável",
        convidarParticipantesAtuais: "Já participam",
        convidarSemParticipantes: "Ninguém participa",
        convidarEscolha: "Quem convidar?",
        convidarConfirmar: "Enviar convite",
        convidarEnviando: "Enviando convite",
        convidarErroCarregar: "Erro ao carregar",
        convidarSemTransferencia: "Convidar não transfere",
      },
      transferir: { cancelar: "Cancelar" },
    },
  }),
}));

import { DialogoConvidar } from "./dialogo-convidar";

const NOME_LONGO = "Maria Aparecida dos Santos Vasconcelos Albuquerque de Oliveira";

function renderizar(props: Partial<Parameters<typeof DialogoConvidar>[0]> = {}) {
  const onFechar = vi.fn();
  const onSucesso = vi.fn();
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const envoltorio = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  );
  render(
    <DialogoConvidar
      atendimentoId="atendimento-1"
      responsavelId="ana-1"
      responsavelNome="Ana Atendente"
      participantes={[{ usuarioId: "caio-1", nome: "Caio Participante", entrouEm: "2026-10-01T10:00:00Z", fotoUrl: null }]}
      aberto
      onFechar={onFechar}
      onSucesso={onSucesso}
      {...props}
    />,
    { wrapper: envoltorio },
  );
  return { onFechar, onSucesso };
}

function urlsChamadas() {
  return estado.chamadas.map((chamada) => `${chamada.metodo} ${chamada.url}`);
}

describe("DialogoConvidar", () => {
  beforeEach(() => {
    estado.destinos = [
      { id: "eu-1", nome: "Eu Mesmo", papel: "ATENDENTE" },
      { id: "ana-1", nome: "Ana Atendente", papel: "ATENDENTE" },
      { id: "caio-1", nome: "Caio Participante", papel: "ATENDENTE" },
      { id: "bruno-1", nome: "Bruno Atendente", papel: "ATENDENTE" },
      { id: "maria-1", nome: NOME_LONGO, papel: "ATENDENTE" },
      { id: "michele-1", nome: "Michele Subgestora", papel: "SUBGESTOR" },
    ];
    estado.falharLista = false;
    estado.respostaConvite = null;
    estado.chamadas = [];
  });

  it("mostra responsável e participantes separados e não oferece quem já está na conversa", async () => {
    renderizar();

    const equipe = screen.getByTestId("convite-equipe-atual");
    expect(equipe).toHaveTextContent("Responsável");
    expect(equipe).toHaveTextContent("Ana Atendente");
    expect(equipe).toHaveTextContent("Já participam");
    expect(equipe).toHaveTextContent("Caio Participante");

    await screen.findByRole("button", { name: "Bruno Atendente" });
    expect(screen.queryByRole("button", { name: "Eu Mesmo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ana Atendente" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Caio Participante" })).not.toBeInTheDocument();
  });

  it("escolher um nome só seleciona; confirmar chama /convidar e nunca /transferir", async () => {
    const { onFechar, onSucesso } = renderizar();

    fireEvent.click(await screen.findByRole("button", { name: "Bruno Atendente" }));
    expect(screen.getByRole("button", { name: "Bruno Atendente" })).toHaveAttribute("aria-pressed", "true");
    expect(urlsChamadas().some((url) => url.startsWith("POST"))).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Enviar convite" }));

    await waitFor(() => expect(onFechar).toHaveBeenCalledTimes(1));
    expect(onSucesso).toHaveBeenCalledTimes(1);
    const posts = estado.chamadas.filter((chamada) => chamada.metodo === "POST");
    expect(posts).toEqual([
      { url: "/api/v1/atendimentos/atendimento-1/convidar", metodo: "POST", corpo: JSON.stringify({ atendenteId: "bruno-1" }) },
    ]);
    expect(urlsChamadas().some((url) => url.includes("/transferir"))).toBe(false);
  });

  it("não declara sucesso antes da resposta e mantém o modal aberto quando a API recusa", async () => {
    let rejeitar: (erro: Error) => void = () => undefined;
    estado.respostaConvite = () => new Promise((_, recusar) => { rejeitar = recusar; });
    const { onFechar, onSucesso } = renderizar();

    fireEvent.click(await screen.findByRole("button", { name: "Bruno Atendente" }));
    fireEvent.click(screen.getByRole("button", { name: "Enviar convite" }));

    const enviando = await screen.findByRole("button", { name: "Enviando convite" });
    expect(enviando).toBeDisabled();
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
    expect(onSucesso).not.toHaveBeenCalled();

    rejeitar(new Error("409"));

    expect(await screen.findByRole("alert")).toHaveTextContent("Erro ao convidar");
    expect(onFechar).not.toHaveBeenCalled();
    expect(onSucesso).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Enviar convite" })).toBeEnabled();
  });

  it("confirmar fica desabilitado sem seleção e cancelar não chama a API", async () => {
    const { onFechar } = renderizar();
    await screen.findByRole("button", { name: "Bruno Atendente" });

    expect(screen.getByRole("button", { name: "Enviar convite" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(onFechar).toHaveBeenCalledTimes(1);
    expect(urlsChamadas().some((url) => url.startsWith("POST"))).toBe(false);
  });

  it("falha ao carregar a lista aparece como erro, não como lista vazia", async () => {
    estado.falharLista = true;
    renderizar();

    expect(await screen.findByRole("alert")).toHaveTextContent("Erro ao carregar");
    expect(screen.queryByText("Nenhum atendente")).not.toBeInTheDocument();
  });

  it("nome longo fica inteiro no title e cortado com reticências, sem vazar do botão", async () => {
    renderizar();

    const botao = await screen.findByRole("button", { name: NOME_LONGO });
    expect(botao).toHaveAttribute("title", NOME_LONGO);
    expect(botao.className).toContain("min-w-0");
    expect(botao.querySelector("span")?.className).toContain("truncate");
  });

  it("mantém subgestor em Outros e permite convidá-lo", async () => {
    renderizar();

    fireEvent.click(await screen.findByRole("button", { name: "Outros" }));
    fireEvent.click(screen.getByRole("button", { name: "Michele Subgestora" }));
    fireEvent.click(screen.getByRole("button", { name: "Enviar convite" }));

    await waitFor(() =>
      expect(estado.chamadas).toContainEqual({
        url: "/api/v1/atendimentos/atendimento-1/convidar",
        metodo: "POST",
        corpo: JSON.stringify({ atendenteId: "michele-1" }),
      }),
    );
  });
});
