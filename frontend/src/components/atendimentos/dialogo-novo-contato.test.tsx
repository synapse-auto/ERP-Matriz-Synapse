import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const textos = {
  botao: "Novo atendimento",
  titulo: "Novo atendimento",
  descricao: "Abra uma conversa em modo humano pelo WhatsApp.",
  nome: "Nome do contato",
  nomePlaceholder: "Nome do contato",
  nomeObrigatorio: "Nome do contato é obrigatório.",
  telefone: "Telefone",
  telefonePlaceholder: "(83) 99999-9999",
  telefoneObrigatorio: "Telefone é obrigatório.",
  primeiraMensagem: "Primeira mensagem (opcional)",
  primeiraMensagemPlaceholder: "Digite a mensagem que será enviada após abrir o atendimento.",
  avisoTemplate:
    "A mensagem será enviada após a abertura do atendimento. Dependendo do canal, pode ser necessário utilizar um template aprovado.",
  cancelar: "Cancelar",
  confirmar: "Iniciar atendimento",
  erro: "Não foi possível iniciar o atendimento.",
};

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({ atendimentos: { novoContato: textos } }),
}));

import { DialogoNovoContato, mascararTelefoneBr } from "./dialogo-novo-contato";

describe("DialogoNovoContato", () => {
  it("mostra os erros de nome e telefone sem enviar", () => {
    const confirmar = vi.fn();
    render(
      <DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(screen.getByText("Nome do contato é obrigatório.")).toBeInTheDocument();
    expect(screen.getByText("Telefone é obrigatório.")).toBeInTheDocument();
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("mascara o telefone brasileiro e envia nome e telefone", () => {
    const confirmar = vi.fn();
    render(
      <DialogoNovoContato aberto onFechar={vi.fn()} onConfirmar={confirmar} />,
    );

    expect(screen.getByPlaceholderText("(83) 99999-9999")).toBeInTheDocument();
    expect(
      screen.getByText(
        "A mensagem será enviada após a abertura do atendimento. Dependendo do canal, pode ser necessário utilizar um template aprovado.",
      ),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Nome do contato"), { target: { value: "Maria" } });
    fireEvent.change(screen.getByLabelText("Telefone"), { target: { value: "83999998888" } });
    expect(screen.getByLabelText("Telefone")).toHaveValue("(83) 99999-8888");

    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));
    expect(confirmar).toHaveBeenCalledWith({ nome: "Maria", telefone: "(83) 99999-8888" });
  });

  it("envia a primeira mensagem só quando preenchida e mostra o erro recebido", () => {
    const confirmar = vi.fn();
    render(
      <DialogoNovoContato
        aberto
        onFechar={vi.fn()}
        onConfirmar={confirmar}
        erro="a janela de 24h esta fechada"
      />,
    );

    fireEvent.change(screen.getByLabelText("Nome do contato"), { target: { value: "Maria" } });
    fireEvent.change(screen.getByLabelText("Telefone"), { target: { value: "83988887777" } });
    fireEvent.change(screen.getByLabelText("Primeira mensagem (opcional)"), {
      target: { value: "  ola  " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Iniciar atendimento" }));

    expect(confirmar).toHaveBeenCalledWith({
      nome: "Maria",
      telefone: "(83) 98888-7777",
      primeiraMensagem: "ola",
    });
    expect(screen.getByText("a janela de 24h esta fechada")).toBeInTheDocument();
  });

  it("cancela sem submeter", () => {
    const fechar = vi.fn();
    const confirmar = vi.fn();
    render(<DialogoNovoContato aberto onFechar={fechar} onConfirmar={confirmar} />);
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(fechar).toHaveBeenCalledTimes(1);
    expect(confirmar).not.toHaveBeenCalled();
  });
});

describe("mascararTelefoneBr", () => {
  it("formata 10 e 11 dígitos", () => {
    expect(mascararTelefoneBr("8333334444")).toBe("(83) 3333-4444");
    expect(mascararTelefoneBr("83999998888")).toBe("(83) 99999-8888");
  });
});
