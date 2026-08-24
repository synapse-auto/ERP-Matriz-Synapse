import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

const mutate = vi.fn();

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    configuracoes: {
      titulo: "Configurações", descricao: "Conta e sistema", abrir: "Abrir configurações",
      perfil: "Perfil do usuário", perfilDescricao: "Nome, e-mail e senha", nome: "Nome", email: "E-mail",
      emailAjuda: "A troca do e-mail deve ser solicitada ao administrador.", telefone: "Telefone", cargo: "Cargo",
      papel: "Papel", senha: "Senha", senhaAjuda: "Altere sua senha.", alterarSenha: "Alterar senha",
      ultimaAlteracaoSenha: "Última alteração", senhaProvisoria: "Ainda não alterada", naoInformado: "Não informado",
      salvarPerfil: "Salvar perfil", salvando: "Salvando...", salvo: "Perfil salvo.", carregando: "Carregando perfil...",
      erro: "Não foi possível carregar ou salvar seu perfil.", erroNome: "Informe um nome válido.",
    },
  }),
}));

vi.mock("@/lib/equipe/use-equipe", () => ({
  useMeuUsuario: () => ({ data: { id: "ana", nome: "Ana Atendente", email: "ana@example.invalid", papel: "ATENDENTE", presenca: "ONLINE", telefone: null, cargo: "Consultora", senhaAlteradaEm: "2026-05-01T00:00:00Z" }, isLoading: false, isError: false }),
  useAtualizarMeuUsuario: () => ({ mutate, isPending: false, isError: false }),
}));

vi.mock("next/link", () => ({ default: ({ children, ...props }: { children: ReactNode; href: string }) => <a {...props}>{children}</a> }));

import { PaginaConfiguracoes } from "./pagina-configuracoes";

describe("pagina de configurações", () => {
  it("edita somente nome e mantém e-mail, papel e senha como leitura/fluxo existente", () => {
    render(<PaginaConfiguracoes />);

    expect(screen.getByDisplayValue("ana@example.invalid")).toBeDisabled();
    expect(screen.getByDisplayValue("Consultora")).toBeDisabled();
    expect(screen.getByText("ATENDENTE")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Alterar senha" })).toHaveAttribute("href", "/trocar-senha");

    fireEvent.change(screen.getByLabelText("Nome"), { target: { value: "Ana Atualizada" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar perfil" }));

    expect(mutate).toHaveBeenCalledWith({ nome: "Ana Atualizada" }, expect.any(Object));
  });
});
