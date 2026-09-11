import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ComponentProps } from "react";

import type { ChatContato } from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";

vi.mock("@/lib/chat-interno/api", () => ({
  listarParticipantesChat: vi.fn(),
  listarContatosChat: vi.fn(),
  adicionarParticipanteChat: vi.fn(),
  removerParticipanteChat: vi.fn(),
  renomearGrupoChat: vi.fn(),
  listarMidiasDoGrupoChat: vi.fn(),
  emitirUrlAssinadaDaMidiaChat: vi.fn(),
}));

import {
  adicionarParticipanteChat,
  listarContatosChat,
  listarParticipantesChat,
  removerParticipanteChat,
  renomearGrupoChat,
  listarMidiasDoGrupoChat,
} from "@/lib/chat-interno/api";
import { PainelLateralGrupo } from "./painel-lateral-grupo";

const textos = {
  detalhes: "Detalhes da conversa",
  fecharDetalhes: "Fechar detalhes da conversa",
  participantesDoGrupo: "Participantes do grupo",
  selecionarParticipantes: "Participantes",
  adicionarParticipante: "Adicionar pessoa",
  removerParticipante: "Remover",
  sairDoGrupo: "Sair do grupo",
  voce: "você",
  renomearGrupo: "Renomear grupo",
  salvarNome: "Salvar nome",
  erroParticipantes: "Não foi possível atualizar os participantes.",
  retrair: "Retrair dados do grupo",
  reabrir: "Reabrir dados do grupo",
  midias: {
    titulo: "Mídias compartilhadas",
    vazio: "Nenhuma mídia compartilhada.",
    carregando: "Carregando mídias...",
    erro: "Não foi possível carregar as mídias.",
    carregarMais: "Carregar mais",
    abrir: "Abrir {nome}",
    baixar: "Baixar {nome}",
  },
} as unknown as Textos["chatInterno"];

const contatos: ChatContato[] = [
  { id: "b1", nome: "Bruno", presenca: "ONLINE" },
  { id: "g1", nome: "Gestora", presenca: "AUSENTE" },
];

function renderizar(props: Partial<ComponentProps<typeof PainelLateralGrupo>> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PainelLateralGrupo
        conversaId="c1"
        nomeAtual="Ops"
        usuarioAtual="u1"
        textos={textos}
        onRetrair={vi.fn()}
        {...props}
      />
    </QueryClientProvider>,
  );
}

describe("PainelLateralGrupo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listarParticipantesChat).mockResolvedValue([
      { id: "u1", nome: "Ana" },
      { id: "b1", nome: "Bruno" },
    ]);
    vi.mocked(listarContatosChat).mockResolvedValue(contatos);
    vi.mocked(adicionarParticipanteChat).mockResolvedValue(undefined);
    vi.mocked(removerParticipanteChat).mockResolvedValue(undefined);
    vi.mocked(renomearGrupoChat).mockResolvedValue(undefined);
    vi.mocked(listarMidiasDoGrupoChat).mockResolvedValue([]);
  });

  it("é um painel fixo, sem modal, e mostra contagem e ações sem hierarquia", async () => {
    renderizar();

    const painel = await screen.findByRole("complementary", { name: "Participantes do grupo" });
    expect(painel).toHaveClass("w-[344px]", "border-l");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await screen.findByText("Bruno");
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Bruno")).toBeInTheDocument();
    expect(screen.getByText("Ana (você)")).toBeInTheDocument();
    expect(screen.getByLabelText("Remover")).toBeInTheDocument();
    expect(screen.getByLabelText("Sair do grupo")).toBeInTheDocument();
    expect(screen.getByLabelText("Renomear grupo")).toBeInTheDocument();
    expect(screen.queryByText(/administrador/i)).toBeNull();
  });

  it("em conversa direta mostra apenas o outro participante e mídias autorizadas", async () => {
    renderizar({ tipo: "DIRETA", nomeAtual: "Bruno", fotoUrl: "/api/v1/me/foto/b1" });

    const painel = await screen.findByRole("complementary", { name: "Detalhes da conversa" });
    expect(painel).toHaveClass("w-[344px]");
    expect(screen.getByText("Bruno")).toBeInTheDocument();
    expect(screen.queryByLabelText("Renomear grupo")).not.toBeInTheDocument();
    expect(screen.queryByText("Adicionar pessoa")).not.toBeInTheDocument();
    expect(screen.queryByText("Participantes do grupo")).not.toBeInTheDocument();
  });

  it("renomeia, adiciona e remove participantes", async () => {
    renderizar();
    await screen.findByText("Bruno");

    fireEvent.change(screen.getByLabelText("Renomear grupo"), { target: { value: "  Operação  " } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar nome" }));
    await waitFor(() => expect(renomearGrupoChat).toHaveBeenCalledWith("c1", "Operação"));

    fireEvent.click(screen.getByRole("button", { name: /Gestora/ }));
    await waitFor(() => expect(adicionarParticipanteChat).toHaveBeenCalledWith("c1", "g1"));

    fireEvent.click(screen.getByLabelText("Remover"));
    await waitFor(() => expect(removerParticipanteChat).toHaveBeenCalledWith("c1", "b1"));
  });

  it("ao sair, retrai o painel e desseleciona a conversa", async () => {
    const onRetrair = vi.fn();
    const onSaiu = vi.fn();
    renderizar({ onRetrair, onSaiu });
    await screen.findByText("Bruno");

    fireEvent.click(screen.getByLabelText("Sair do grupo"));

    await waitFor(() => {
      expect(removerParticipanteChat).toHaveBeenCalledWith("c1", "u1");
      expect(onSaiu).toHaveBeenCalledOnce();
      expect(onRetrair).toHaveBeenCalledOnce();
    });
  });

  it("mantém erros de mutation visíveis dentro do painel", async () => {
    vi.mocked(renomearGrupoChat).mockRejectedValueOnce(new Error("falha"));
    renderizar();
    await screen.findByText("Bruno");

    fireEvent.change(screen.getByLabelText("Renomear grupo"), { target: { value: "Novo nome" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar nome" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível atualizar os participantes.");
  });
});
