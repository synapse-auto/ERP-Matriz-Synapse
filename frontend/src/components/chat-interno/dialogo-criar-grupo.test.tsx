import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { ChatContato } from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";

import { DialogoCriarGrupo } from "./dialogo-criar-grupo";

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
  retrair: "Retrair dados do grupo",
  reabrir: "Reabrir dados do grupo",
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
