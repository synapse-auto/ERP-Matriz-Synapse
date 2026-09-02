import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useCallback, useState } from "react";
import { describe, expect, it, vi } from "vitest";

import type { ItemInbox } from "@/lib/atendimento/types";

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimentosVisiveis: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useQuantidadeAtendimentosFinalizaveis: () => ({
    data: { quantidade: 0, porAtendente: [] },
    isLoading: false,
  }),
}));

const authMock = vi.hoisted(() => ({ papel: "GESTOR" as string | null }));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: typeof authMock) => unknown) => seletor(authMock),
}));

vi.mock("@/lib/atendimento/api", () => ({
  listarInboxUnificada: vi.fn(),
  listarAtendimentos: vi.fn(),
  contarAtendimentosPorVisao: vi.fn(),
  finalizarAtendimentosVisiveis: vi.fn(),
  contarAtendimentosFinalizaveis: vi.fn(),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    menu: { itens: { atendimentos: "Atendimentos" } },
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      lista: {
        busca: "Buscar cliente ou protocolo...",
        filtros: "Filtros da lista",
        carregarMais: "Carregar mais conversas",
        carregandoMais: "Carregando conversas...",
      },
      visoes: { todos: "Todos", ativos: "Ativos", pendentes: "Pendentes", potenciais: "Potenciais" },
      filtros: { etapa: "Etapa", atendente: "Atendente" },
      cartao: { semAtendente: "Sem atendente", vazio: "Nenhuma conversa", naoLidas: "{quantidade} mensagens não lidas" },
      novoContato: { botao: "Novo atendimento" },
      finalizar: {
        todosMenu: "Mais ações",
        todos: "Finalizar Todos",
        todosTitulo: "Finalizar atendimentos",
        todosDescricao: "Encerrar {quantidade}",
        todosConfirmar: "Finalizar {quantidade}",
        todosCancelar: "Voltar",
        todosResultado: "{finalizados} finalizados; {recusados} recusados",
        todosErro: "Erro",
      },
    },
    chatInterno: { titulo: "Equipe", novaConversa: "Nova conversa", selecionarPessoa: "Selecionar pessoa" },
  }),
}));

import * as api from "@/lib/atendimento/api";

import { ListaConversas } from "./lista-conversas";

const cliente = (id: string, nome: string): ItemInbox => ({
  tipo: "CLIENTE",
  atendimentoId: id,
  leadId: `lead-${id}`,
  leadNome: nome,
  leadFotoUrl: null,
  identificadorVisual: id,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: null,
  atendenteNome: null,
  ultimaMensagemPreview: "mensagem do cliente",
  ultimaMensagemRemetenteTipo: "LEAD",
  ultimaMensagemEm: "2026-08-26T12:00:00Z",
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
});

const equipe = (id: string, nome: string): ItemInbox => ({
  tipo: "EQUIPE_INTERNA",
  atendimentoId: null,
  conversaId: id,
  nome,
  avatarUrl: null,
  identificadorVisual: id,
  ultimaMensagemPreview: "mensagem interna",
  ultimaMensagemEm: "2026-08-26T11:00:00Z",
  naoLidas: 1,
  participantes: "Ana, Bruno",
  tipoConversa: "DIRETA",
});

describe("ListaConversas — regressão de identidade do array", () => {
  it("repassa cliente e equipe ao pai sem ciclo e atualiza uma vez por página", async () => {
    const primeira = [cliente("cliente-1", "Cliente um"), equipe("conversa-1", "Equipe interna")];
    const segunda = [equipe("conversa-2", "Equipe atualizada"), cliente("cliente-2", "Cliente dois")];
    vi.mocked(api.listarInboxUnificada).mockResolvedValue({ itens: primeira, proximoCursor: null });
    vi.mocked(api.contarAtendimentosPorVisao).mockResolvedValue({ TODOS: 2, ATIVOS: 1, PENDENTES: 0, POTENCIAIS: 0 });

    const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const repasses: ItemInbox[][] = [];
    const abrir = vi.fn();

    function Tela() {
      const [itens, setItens] = useState<ItemInbox[]>([]);
      const repassar = useCallback((proximos: ItemInbox[]) => {
        repasses.push(proximos);
        setItens(proximos);
      }, []);
      return (
        <>
          <ListaConversas
            selecionadoId={null}
            onAbrirAtendimento={abrir}
            onAtendimentosAtualizados={repassar}
          />
          <output data-testid="ordem">{itens.map((item) => item.identificadorVisual).join(",")}</output>
        </>
      );
    }

    render(<QueryClientProvider client={cache}><Tela /></QueryClientProvider>);
    await waitFor(() => expect(screen.getByTestId("ordem")).toHaveTextContent("cliente-1,conversa-1"));
    expect(document.querySelector('[data-slot="lista-conversas-itens"]')).toHaveClass("pt-4");
    expect(repasses).toHaveLength(2);

    cache.setQueryData(["atendimentos", "inbox", "TODOS"], {
      pages: [{ itens: segunda, proximoCursor: null }],
      pageParams: [null],
    });
    await waitFor(() => expect(screen.getByTestId("ordem")).toHaveTextContent("conversa-2,cliente-2"));
    expect(repasses).toHaveLength(3);
    expect(repasses[2].map((item) => item.tipo)).toEqual(["EQUIPE_INTERNA", "CLIENTE"]);
    expect(() => fireEvent.click(screen.getByRole("button", { name: /Equipe atualizada/ }))).not.toThrow();
    expect(() => fireEvent.click(screen.getByRole("button", { name: /Cliente dois/ }))).not.toThrow();
    expect(abrir).toHaveBeenCalledTimes(2);
  });
});
