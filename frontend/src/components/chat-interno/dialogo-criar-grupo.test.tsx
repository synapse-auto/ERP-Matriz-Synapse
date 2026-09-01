import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import type { ChatContato } from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";

vi.mock("@/lib/chat-interno/api", () => ({
  listarParticipantesChat: vi.fn(),
  listarContatosChat: vi.fn(),
  adicionarParticipanteChat: vi.fn(),
  removerParticipanteChat: vi.fn(),
  renomearGrupoChat: vi.fn(),
}));

import {
  listarContatosChat,
  listarParticipantesChat,
} from "@/lib/chat-interno/api";
import { DialogoCriarGrupo } from "./dialogo-criar-grupo";
import { PainelParticipantesGrupo } from "./painel-participantes-grupo";

const textos = {
  novoGrupo: "Novo grupo",
  criarGrupo: "Criar grupo",
  nomeDoGrupo: "Nome do grupo",
  nomeDoGrupoPlaceholder: "Ex.",
  selecionarParticipantes: "Participantes",
  selecionarParticipantesDescricao: "Escolha quem entra.",
  participantesMinimos: "Selecione ao menos uma outra pessoa.",
  erroCriarGrupo: "Erro ao criar",
  buscarPessoa: "Buscar",
  fecharSeletor: "Fechar",
  participantesDoGrupo: "Participantes do grupo",
  adicionarParticipante: "Adicionar pessoa",
  removerParticipante: "Remover",
  sairDoGrupo: "Sair do grupo",
  voce: "você",
  renomearGrupo: "Renomear",
  salvarNome: "Salvar nome",
  erroParticipantes: "Erro participantes",
} as Textos["chatInterno"];

const contatos: ChatContato[] = [
  { id: "b1", nome: "Bruno", presenca: "ONLINE" },
  { id: "g1", nome: "Gestora", presenca: "AUSENTE" },
];

describe("DialogoCriarGrupo", () => {
  it("cria grupo com nome e participantes sem papel de admin", async () => {
    const onCriar = vi.fn().mockResolvedValue({ id: "grupo-1" });
    render(
      <DialogoCriarGrupo
        aberto
        onFechar={vi.fn()}
        contatos={contatos}
        onCriar={onCriar}
        textos={textos}
      />,
    );

    expect(screen.queryByText(/admin/i)).toBeNull();
    fireEvent.change(screen.getByLabelText("Nome do grupo"), { target: { value: "Ops" } });
    fireEvent.click(screen.getByRole("button", { name: /Bruno/ }));
    fireEvent.click(screen.getByRole("button", { name: "Criar grupo" }));

    await waitFor(() => expect(onCriar).toHaveBeenCalledWith("Ops", ["b1"]));
  });
});

describe("PainelParticipantesGrupo", () => {
  beforeEach(() => {
    vi.mocked(listarParticipantesChat).mockResolvedValue([
      { id: "u1", nome: "Ana" },
      { id: "b1", nome: "Bruno" },
    ]);
    vi.mocked(listarContatosChat).mockResolvedValue(contatos);
  });

  it("mostra as mesmas acoes para qualquer membro — sem UI de administrador", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <PainelParticipantesGrupo
          aberto
          onFechar={vi.fn()}
          conversaId="c1"
          nomeAtual="Ops"
          usuarioAtual="u1"
          textos={textos}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByText("Bruno")).toBeInTheDocument());
    expect(screen.getByLabelText("Remover")).toBeInTheDocument();
    expect(screen.getByLabelText("Sair do grupo")).toBeInTheDocument();
    expect(screen.queryByText(/administrador/i)).toBeNull();
    expect(screen.getByLabelText("Renomear")).toBeInTheDocument();
  });
});
