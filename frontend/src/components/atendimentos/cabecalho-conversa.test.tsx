import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CartaoAtendimento } from "@/lib/atendimento/types";

const finalizar = vi.fn();
const participacao = vi.hoisted(() => ({
  usuarioId: "usuario-1",
  papel: "GESTOR",
  participantes: [] as Array<{ usuarioId: string; nome: string; fotoUrl?: string | null }>,
  meuPedido: null as { status: "PENDENTE" | "RECUSADO"; solicitanteNome?: string; solicitadoEm?: string } | null,
  pedidosPendentes: [] as Array<{ id: string; solicitanteNome: string; solicitadoEm: string }>,
  recarregar: vi.fn(),
  invalidar: vi.fn(),
  pedir: vi.fn(),
  entrar: vi.fn(),
  sair: vi.fn(),
  aprovar: vi.fn(),
  recusar: vi.fn(),
}));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: { accessToken: string | null; papel: string; usuarioId: string }) => unknown) =>
    seletor({ accessToken: null, papel: participacao.papel, usuarioId: participacao.usuarioId }),
}));

vi.mock("@/lib/atendimento/use-participantes", () => ({
  useParticipantes: () => ({ data: participacao.participantes, recarregar: participacao.recarregar }),
}));

vi.mock("@/lib/atendimento/use-participacao", () => ({
  aprovarPedido: participacao.aprovar,
  entrarAtendimento: participacao.entrar,
  invalidarParticipacao: participacao.invalidar,
  pedirEntrada: participacao.pedir,
  recusarPedido: participacao.recusar,
  sairAtendimento: participacao.sair,
  useMeuPedido: () => participacao.meuPedido,
  usePedidosPendentes: () => participacao.pedidosPendentes,
}));

vi.mock("@/lib/atendimento/use-transferir-finalizar", () => ({
  useFinalizarAtendimento: () => ({ mutate: finalizar, isPending: false }),
}));

vi.mock("@/lib/lead/use-painel-lead", () => ({
  useLead: () => ({
    data: { telefone: "(61) 99999-0000", empresa: "Vidraçaria Central" },
  }),
}));

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    atendimentos: {
      canais: { whatsapp: "WhatsApp" },
      cabecalho: {
        atendidoPor: "Atendido por",
        semAtendente: "Sem atendente",
        transferir: "Transferir",
        finalizar: "Finalizar",
        buscar: "Buscar na conversa",
        novoAtendimento: "Reativar atendimento",
        participantes: "Participantes",
        participando: "Você está participando",
        pedirEntrada: "Pedir para entrar",
        pedidoPendente: "Pedido pendente",
        entrar: "Entrar no atendimento",
        entrarDescricao: "Entrar adiciona você como participante; o responsável não muda.",
        pedirEntradaDescricao: "O responsável precisa aprovar; o atendimento não será transferido.",
        sair: "Sair do atendimento",
        recusado: "Pedido recusado",
        aprovarEntrada: "Aceitar pedido",
        recusarEntrada: "Recusar pedido",
        pedidoEnviado: "Pedido enviado ao responsável {nome}.",
        pedidoValidadeConfigurada: "A validade segue a configuração da instância.",
        pedidoRecebido: "{nome} pediu para entrar",
        pedidoSolicitadoEm: "Solicitado em {horario}",
        avisoEnviarAssume: "Ao enviar agora, você assume este atendimento.",
        sucessoEntrou: "Você entrou no atendimento.",
        sucessoPedido: "Pedido enviado. O responsável será avisado.",
        sucessoSaiu: "Você saiu do atendimento.",
        sucessoAprovado: "{nome} agora participa do atendimento.",
        sucessoRecusado: "Pedido de {nome} recusado.",
        erroSemPermissao: "Você não tem permissão para entrar diretamente neste atendimento.",
        erroPedidoExpirado: "Esse pedido expirou. Solicite novamente.",
        erroParticipacaoNaoEncontrada: "Sua participação não está mais ativa.",
        erroParticipacao: "Não foi possível atualizar sua participação.",
      },
      finalizar: {
        titulo: "Finalizar atendimento",
        descricao: "",
        confirmar: "Finalizar",
        cancelar: "Cancelar",
        sucesso: "Finalizado",
        erro: "Erro",
        todosMenu: "Mais ações",
        todos: "Finalizar todos",
        todosTitulo: "Finalizar atendimentos",
        todosDescricao: "Encerrar {quantidade}",
        todosConfirmar: "Finalizar {quantidade}",
        todosCancelar: "Cancelar",
        todosResultado: "{finalizados} finalizados; {recusados} recusados",
        todosErro: "Erro",
      },
      avaliacao: {
        titulo: "Avaliação",
        descricao: "Nota 1 a 5",
        registrar: "Avaliar",
        confirmar: "Salvar",
        cancelar: "Agora não",
        jaRegistrada: "Nota {nota}",
        sucesso: "Ok",
        erro: "Erro",
        nota: "Nota {nota}",
      },
      painel: {
        reabrir: "Reabrir detalhes do lead",
      },
    },
    painelLead: { dados: { telefone: "Telefone" } },
  }),
}));

vi.mock("./atalho-tags", () => ({
  AtalhoTags: () => <button type="button">Etiquetar</button>,
}));

vi.mock("./dialogo-transferir", () => ({
  DialogoTransferir: () => null,
}));

import { CabecalhoConversa } from "./cabecalho-conversa";

const conversa: CartaoAtendimento = {
  atendimentoId: "atendimento-1",
  leadId: "lead-1",
  leadNome: "Ana Vidros",
  leadFotoUrl: null,
  leadEmpresa: "Empresa antiga",
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: "usuario-1",
  atendenteNome: "Jardel Lima",
  ultimaMensagemPreview: null,
  ultimaMensagemRemetenteTipo: null,
  ultimaMensagemEm: null,
  ultimaMensagemDoLeadEm: null,
  naoLidas: 0,
};

describe("CabecalhoConversa", () => {
  beforeEach(() => {
    participacao.papel = "GESTOR";
    participacao.participantes = [];
    participacao.meuPedido = null;
    participacao.pedidosPendentes = [];
    participacao.recarregar.mockReset().mockResolvedValue(undefined);
    participacao.invalidar.mockReset();
    participacao.pedir.mockReset().mockResolvedValue(undefined);
    participacao.entrar.mockReset().mockResolvedValue(undefined);
    participacao.sair.mockReset().mockResolvedValue(undefined);
    participacao.aprovar.mockReset().mockResolvedValue(undefined);
    participacao.recusar.mockReset().mockResolvedValue(undefined);
  });

  it("mostra contexto real e oferece ações funcionais do protótipo", () => {
    const alternarBusca = vi.fn();
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={alternarBusca}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText("WhatsApp")).toBeInTheDocument();
    expect(
      screen.getByText(
        /\(61\) 99999-0000 · Vidraçaria Central · Atendido por Jardel Lima/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Telefone/ })).toHaveAttribute(
      "href",
      "tel:61999990000",
    );

    fireEvent.click(screen.getByRole("button", { name: "Buscar na conversa" }));
    expect(alternarBusca).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByRole("button", { name: "Finalizar" }));
    expect(finalizar).toHaveBeenCalledWith("atendimento-1");
  });

  it("mantém a ação individual e não oferece o menu global de finalização", () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: "Mais ações" })).not.toBeInTheDocument();
    expect(screen.queryByText("Finalizar todos")).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Finalizar" })).toBeEnabled();
    expect(screen.getAllByRole("button", { name: "Transferir" }).length).toBeGreaterThan(0);
  });

  it("oferece reabrir os detalhes somente quando o painel está retraído", () => {
    const onAlternar = vi.fn();
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto={false}
        onAlternarPainelDetalhes={onAlternar}
      />,
    );

    const controle = screen.getByRole("button", { name: "Reabrir detalhes do lead" });
    expect(controle).toHaveAttribute("aria-expanded", "false");
    expect(controle).toHaveAttribute("aria-controls", "painel-detalhes-lead");
    fireEvent.click(controle);
    expect(onAlternar).toHaveBeenCalledOnce();
  });

  it("abre um atendimento novo a partir do finalizado sem alterar o significado do estado", () => {
    const abrirNovo = vi.fn();
    render(
      <CabecalhoConversa
        conversa={{ ...conversa, status: "FINALIZADO" }}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
        onAbrirNovoAtendimento={abrirNovo}
      />,
    );

    expect(screen.queryByRole("button", { name: "Finalizar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Avaliar" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Reativar atendimento" }));
    expect(abrirNovo).toHaveBeenCalledOnce();
  });

  it("mostra erro legível ao falhar a entrada e mantém o estado fora", async () => {
    participacao.entrar.mockRejectedValueOnce(new Error("sem alçada para entrar diretamente"));
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Entrar no atendimento" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Você não tem permissão para entrar diretamente neste atendimento.");
    expect(screen.queryByRole("button", { name: "Sair do atendimento" })).not.toBeInTheDocument();
  });

  it("confirma a entrada e oferece sair do atendimento", async () => {
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Entrar no atendimento" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Você entrou no atendimento.");
    expect(await screen.findByRole("button", { name: "Sair do atendimento" })).toBeInTheDocument();
  });

  it("distingue participantes do responsável", () => {
    participacao.participantes = [{ usuarioId: "usuario-2", nome: "Ana Beatriz" }];
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText(/Atendido por Jardel Lima/)).toBeInTheDocument();
    expect(screen.getByLabelText("Participantes")).toBeInTheDocument();
    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
  });

  it("mostra o nome de cada solicitante ao responsável", () => {
    participacao.pedidosPendentes = [
      { id: "pedido-1", solicitanteNome: "Ana Beatriz", solicitadoEm: "2026-09-01T10:00:00Z" },
      { id: "pedido-2", solicitanteNome: "Carlos Silva", solicitadoEm: "2026-09-01T10:01:00Z" },
    ];
    render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText("Ana Beatriz")).toBeInTheDocument();
    expect(screen.getByText("Carlos Silva")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Aceitar pedido" })).toHaveLength(2);
  });

  it("avisa que o envio assume o atendimento somente fora da participação", () => {
    const { rerender } = render(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(screen.getByText("Ao enviar agora, você assume este atendimento.")).toBeInTheDocument();
    participacao.participantes = [{ usuarioId: "usuario-1", nome: "Jardel Lima" }];
    rerender(
      <CabecalhoConversa
        conversa={conversa}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );
    expect(screen.queryByText("Ao enviar agora, você assume este atendimento.")).not.toBeInTheDocument();
  });

  it("mantém o cabeçalho legível em viewport móvel", () => {
    render(
      <CabecalhoConversa
        conversa={{ ...conversa, leadNome: "Cliente com um nome bastante comprido para testar o cabeçalho" }}
        buscaAberta={false}
        onAlternarBusca={vi.fn()}
        painelDetalhesAberto
        onAlternarPainelDetalhes={vi.fn()}
      />,
    );

    expect(document.querySelector('[data-slot="cabecalho-conversa"]')).toHaveClass("flex-wrap", "min-h-[72px]");
  });
});
