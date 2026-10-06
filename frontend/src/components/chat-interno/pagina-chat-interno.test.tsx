import type { ReactNode } from "react";

import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ChatConversa } from "@/lib/chat-interno/types";
import { definirCapacidadesDeTeste } from "@/test/capacidades-de-teste";

const capturas = vi.hoisted(() => ({
  lista: null as null | { onEncaminharCliente?: (mensagem: unknown) => void },
  dialogoCliente: null as null | { mensagem: unknown },
  painelGrupo: null as null | { fotoUrl?: string | null; podeAlterarFoto?: boolean },
}));

const textos = vi.hoisted(() => ({
  titulo: "Chat interno",
  encaminharCliente: { acao: "Encaminhar para o cliente", titulo: "Encaminhar para o cliente", statusTitulo: "Acompanhamento do envio", previaTitulo: "Confirme o envio ao cliente", descricao: "Escolha o atendimento.", voltar: "Voltar", cancelar: "Cancelar", fechar: "Fechar", enviando: "Enviando…", confirmar: "Enviar ao cliente" },
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
    fotoAlterada: "alterou a foto do grupo",
    fotoRemovida: "removeu a foto do grupo",
  },
  fotoGrupo: { fotoAlt: "Foto do grupo {nome}" },
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
  responderMensagemChat: vi.fn(),
  encaminharMensagemChat: vi.fn(),
  excluirMensagemChat: vi.fn(),
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
  ListaMensagensChatInterno: (props: { onEncaminharCliente?: (mensagem: unknown) => void }) => {
    capturas.lista = props;
    return null;
  },
  DialogoEncaminharChatInterno: () => null,
}));

vi.mock("./dialogo-encaminhar-ao-cliente", () => ({
  DialogoEncaminharAoCliente: (props: { mensagem: unknown }) => {
    capturas.dialogoCliente = props;
    return null;
  },
}));

vi.mock("./avatar-do-grupo", () => ({
  AvatarDoGrupo: ({ id, fotoUrl }: { id: string; fotoUrl?: string | null }) => (
    <span data-testid={`avatar-grupo-${id}`} data-foto={fotoUrl ?? ""} />
  ),
}));

vi.mock("./dialogo-selecionar-pessoa", () => ({ DialogoSelecionarPessoa: () => null }));
vi.mock("./dialogo-criar-grupo", () => ({ DialogoCriarGrupo: () => null }));
vi.mock("./painel-lateral-grupo", () => ({
  PainelLateralGrupo: (props: { onRetrair: () => void; fotoUrl?: string | null; podeAlterarFoto?: boolean }) => {
    capturas.painelGrupo = props;
    return (
      <aside data-testid="painel-lateral-grupo" data-foto={props.fotoUrl ?? ""} data-pode-alterar-foto={String(Boolean(props.podeAlterarFoto))}>
        <button type="button" onClick={props.onRetrair}>Retrair painel</button>
      </aside>
    );
  },
}));

import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
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

let cicloDaConexao = 1;
let notificar: ((evento: never) => void) | undefined;

describe("PaginaChatInterno", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    cicloDaConexao = 1;
    notificar = undefined;
    vi.mocked(useConexaoTempoReal).mockImplementation(((_token: unknown, _revogacao: unknown, aoNotificar: (e: never) => void) => {
      notificar = aoNotificar;
      return { conexao: {} as never, estado: "conectado" as const, ciclo: cicloDaConexao };
    }) as never);
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

  describe("encaminhar para o cliente", () => {
    const mensagem = { id: "m1", conversaId: "g1", remetenteId: "u2", remetenteNome: "Bruno", tipo: "TEXTO", conteudo: "oi", enviadoEm: "2026-10-05T12:00:00Z" };

    beforeEach(() => {
      capturas.lista = null;
      capturas.dialogoCliente = null;
      definirCapacidadesDeTeste({});
    });

    it("com permissão de responder, a lista recebe a ação e escolher uma mensagem abre o diálogo", async () => {
      renderizar();
      fireEvent.click(await screen.findByRole("button", { name: /Grupo 1/ }));

      await waitFor(() => expect(capturas.lista?.onEncaminharCliente).toBeTypeOf("function"));
      expect(capturas.dialogoCliente?.mensagem).toBeNull();
      act(() => capturas.lista!.onEncaminharCliente!(mensagem));

      await waitFor(() => expect(capturas.dialogoCliente?.mensagem).toEqual(mensagem));
    });

    it("sem a permissão atendimentos.responder, a ação não é oferecida (o backend ainda confere)", async () => {
      definirCapacidadesDeTeste({ negadas: ["atendimentos.responder"] });
      renderizar();
      fireEvent.click(await screen.findByRole("button", { name: /Grupo 1/ }));

      await waitFor(() => expect(capturas.lista).not.toBeNull());
      expect(capturas.lista?.onEncaminharCliente).toBeUndefined();
    });
  });

  describe("foto do grupo", () => {
    const comFoto = (versao: number | null): ChatConversa[] => [
      { ...conversas[0], fotoUrl: versao === null ? null : `/api/v1/chat-interno/conversas/g1/foto?v=${versao}` },
      conversas[1],
      conversas[2],
    ];

    function renderizarComRecarga() {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      // Elemento novo a cada render: o React reaproveita uma arvore identica e nao re-renderizaria.
      const arvore = () => (
        <QueryClientProvider client={client}>
          <PaginaChatInterno />
        </QueryClientProvider>
      );
      const resultado = render(arvore());
      return { recarregar: () => resultado.rerender(arvore()) };
    }

    it("a lista entrega ao avatar a foto de cada grupo; sem foto, nada (fallback do avatar)", async () => {
      vi.mocked(listarConversasChat).mockResolvedValue(comFoto(1));

      renderizar();

      expect(await screen.findByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", "/api/v1/chat-interno/conversas/g1/foto?v=1");
      expect(screen.getByTestId("avatar-grupo-g2")).toHaveAttribute("data-foto", "");
      expect(screen.queryByTestId("avatar-grupo-d1")).not.toBeInTheDocument();
    });

    it("o painel de detalhes recebe a foto e a permissão de edição decididas pelo backend", async () => {
      vi.mocked(listarConversasChat).mockResolvedValue([
        { ...conversas[0], fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=9", podeAlterarFoto: true },
        ...conversas.slice(1),
      ]);
      capturas.painelGrupo = null;

      renderizar();
      fireEvent.click(await screen.findByRole("button", { name: /Grupo 1/ }));

      const painel = await screen.findByTestId("painel-lateral-grupo");
      expect(painel).toHaveAttribute("data-foto", "/api/v1/chat-interno/conversas/g1/foto?v=9");
      expect(painel).toHaveAttribute("data-pode-alterar-foto", "true");
      expect(capturas.painelGrupo).toMatchObject({
        fotoUrl: "/api/v1/chat-interno/conversas/g1/foto?v=9",
        podeAlterarFoto: true,
      });
    });

    it("evento de tempo real do chat recarrega a lista e a foto nova aparece sem F5", async () => {
      vi.mocked(listarConversasChat).mockResolvedValueOnce(comFoto(1)).mockResolvedValue(comFoto(2));
      renderizar();
      expect(await screen.findByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", expect.stringContaining("v=1"));

      await act(async () => {
        notificar?.({ tipo: "CHAT_INTERNO_MENSAGEM", dados: { conversaId: "g1" } } as never);
      });

      await waitFor(() => expect(screen.getByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", expect.stringContaining("v=2")));
    });

    it("foto removida por outro participante volta ao fallback depois do evento", async () => {
      vi.mocked(listarConversasChat).mockResolvedValueOnce(comFoto(1)).mockResolvedValue(comFoto(null));
      renderizar();
      expect(await screen.findByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", expect.stringContaining("v=1"));

      await act(async () => {
        notificar?.({ tipo: "CHAT_INTERNO_MENSAGEM", dados: { conversaId: "g1" } } as never);
      });

      await waitFor(() => expect(screen.getByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", ""));
    });

    it("reconexão do WebSocket recarrega a lista: o evento perdido enquanto estava fora não deixa a foto velha", async () => {
      vi.mocked(listarConversasChat).mockResolvedValueOnce(comFoto(1)).mockResolvedValue(comFoto(2));
      const { recarregar } = renderizarComRecarga();
      expect(await screen.findByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", expect.stringContaining("v=1"));
      expect(listarConversasChat).toHaveBeenCalledTimes(1);

      cicloDaConexao = 2;
      act(() => recarregar());

      await waitFor(() => expect(screen.getByTestId("avatar-grupo-g1")).toHaveAttribute("data-foto", expect.stringContaining("v=2")));
      expect(listarConversasChat).toHaveBeenCalledTimes(2);
    });

    it("a primeira conexão não recarrega a lista à toa", async () => {
      vi.mocked(listarConversasChat).mockResolvedValue(comFoto(1));
      renderizar();
      await screen.findByTestId("avatar-grupo-g1");

      await act(async () => {});

      expect(listarConversasChat).toHaveBeenCalledTimes(1);
    });
  });
});
