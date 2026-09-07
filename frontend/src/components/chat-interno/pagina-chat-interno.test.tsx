import type { ReactNode } from "react";

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ChatConversa } from "@/lib/chat-interno/types";

const textos = vi.hoisted(() => ({
  titulo: "Chat interno",
  novaConversa: "Nova conversa",
  novoGrupo: "Novo grupo",
  conversas: "Conversas",
  semConversas: "Sem conversas",
  selecioneConversa: "Selecione uma conversa",
  semMensagens: "Nenhuma mensagem",
  carregando: "Carregando",
  erro: "Erro",
  tipoGrupo: "Grupo",
  tipoDireta: "Conversa direta",
  sistema: {
    grupoCriado: "criou o grupo {nome}",
    participanteAdicionado: "adicionou {alvo}",
    participanteRemovido: "removeu {alvo}",
    participanteSaiu: "{alvo} saiu do grupo",
    nomeAlterado: "renomeou o grupo para {nome}",
    eventoDesconhecido: "atualização do grupo",
  },
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    chatInterno: textos,
    atendimentos: { composer: { anexoSoltar: "Solte os arquivos aqui" } },
  }),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: Object.assign(
    (seletor: (estado: { usuarioId: string; accessToken: string }) => unknown) =>
      seletor({ usuarioId: "u1", accessToken: "token" }),
    { getState: () => ({ usuarioId: "u1", accessToken: "token" }) },
  ),
}));

vi.mock("@/lib/atendimento/tempo-real", () => ({
  useConexaoTempoReal: vi.fn(),
}));

vi.mock("@/lib/chat-interno/api", () => ({
  listarContatosChat: vi.fn(),
  listarConversasChat: vi.fn(),
  listarMensagensChat: vi.fn(),
  abrirConversaDireta: vi.fn(),
  criarGrupoChat: vi.fn(),
  enviarMensagemChat: vi.fn(),
  enviarMidiaChat: vi.fn(),
  marcarChatComoLido: vi.fn(),
  definirReacaoChat: vi.fn(),
  removerReacaoChat: vi.fn(),
}));

vi.mock("@/lib/atendimento/reacoes-cache", () => ({
  atualizarReacoesDoChatInterno: vi.fn(),
  substituirReacoesDoChatInterno: vi.fn(),
}));

vi.mock("@/components/atendimentos/zona-soltar-arquivos", () => ({
  ZonaSoltarArquivos: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("./componentes-chat-interno", () => ({
  CabecalhoChatInterno: ({
    conversa,
    painelGrupoAberto,
    onGerenciarGrupo,
  }: {
    conversa: ChatConversa;
    painelGrupoAberto?: boolean;
    onGerenciarGrupo?: () => void;
  }) => (
    <header>
      <span>{conversa.participantes}</span>
      {conversa.tipo === "GRUPO" && !painelGrupoAberto && (
        <button type="button" onClick={onGerenciarGrupo}>Abrir painel</button>
      )}
    </header>
  ),
  ComposerChatInterno: () => null,
  ListaMensagensChatInterno: () => null,
}));

vi.mock("./dialogo-selecionar-pessoa", () => ({ DialogoSelecionarPessoa: () => null }));
vi.mock("./dialogo-criar-grupo", () => ({ DialogoCriarGrupo: () => null }));
vi.mock("./painel-lateral-grupo", () => ({
  PainelLateralGrupo: ({ onRetrair }: { onRetrair: () => void }) => (
    <aside data-testid="painel-lateral-grupo">
      <button type="button" onClick={onRetrair}>Retrair painel</button>
    </aside>
  ),
}));

import {
  listarContatosChat,
  listarConversasChat,
  listarMensagensChat,
  marcarChatComoLido,
} from "@/lib/chat-interno/api";
import { PaginaChatInterno } from "./pagina-chat-interno";

const conversas: ChatConversa[] = [
  { id: "g1", tipo: "GRUPO", participantes: "Grupo 1", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0 },
  { id: "d1", tipo: "DIRETA", participantes: "Pessoa direta", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0 },
  { id: "g2", tipo: "GRUPO", participantes: "Grupo 2", ultimaMensagem: null, ultimaMensagemEm: null, naoLidas: 0 },
];

function renderizar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PaginaChatInterno />
    </QueryClientProvider>,
  );
}

describe("PaginaChatInterno", () => {
  beforeEach(() => {
    vi.mocked(listarConversasChat).mockResolvedValue(conversas);
    vi.mocked(listarContatosChat).mockResolvedValue([]);
    vi.mocked(listarMensagensChat).mockResolvedValue({ mensagens: [], proximoCursor: null });
    vi.mocked(marcarChatComoLido).mockResolvedValue(undefined);
  });

  it("abre grupos por padrão, esconde o painel em diretas e reabre ao trocar para outro grupo", async () => {
    renderizar();

    fireEvent.click(await screen.findByRole("button", { name: /Grupo 1/ }));
    expect(await screen.findByTestId("painel-lateral-grupo")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retrair painel" }));
    expect(screen.queryByTestId("painel-lateral-grupo")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Abrir painel" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Pessoa direta/ }));
    await waitFor(() => {
      expect(screen.queryByTestId("painel-lateral-grupo")).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Abrir painel" })).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Grupo 2/ }));
    expect(await screen.findByTestId("painel-lateral-grupo")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Abrir painel" })).not.toBeInTheDocument();
  });
});
