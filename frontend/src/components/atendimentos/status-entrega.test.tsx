import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { TextosProvider } from "@/lib/config/textos-provider";
import type { Textos } from "@/lib/config/schema";

import { StatusEntregaIcone } from "./status-entrega";

type ParcialProfundo<T> = T extends object
  ? { [Chave in keyof T]?: ParcialProfundo<T[Chave]> }
  : T;

const TEXTOS_FIXTURE = {
  app: { nome: "", marca: "", subtitulo: "" },
  menu: { grupoMenu: "", grupoGestao: "", itens: {} },
  rodape: {
    trocarConta: "",
    sair: "",
    presenca: { rotulo: "", online: "", ausente: "", offline: "" },
  },
  login: {
    titulo: "",
    subtitulo: "",
    campoEmail: "",
    campoSenha: "",
    botaoEntrar: "",
    entrando: "",
    erroCredenciais: "",
    erroGenerico: "",
  },
  estados: {
    carregando: "",
    vazio: "",
    erroGenerico: "",
    tentarNovamente: "",
    emConstrucao: "",
    sessaoExpirada: "",
  },
  atendimentos: {
    visoes: { ativos: "", pendentes: "", potenciais: "", todos: "" },
    filtros: { etapa: "", status: "", tag: "", atendente: "", busca: "" },
    cartao: { semAtendente: "", vazio: "" },
    cabecalho: { atendidoPor: "", semAtendente: "", transferir: "", finalizar: "", buscar: "" },
    transferir: {
      titulo: "",
      descricao: "",
      devolverParaIa: "",
      assumirParaMim: "",
      confirmar: "",
      cancelar: "",
      sucesso: "",
      erro: "",
    },
    finalizar: { titulo: "", descricao: "", confirmar: "", cancelar: "", sucesso: "", erro: "" },
    mensagem: {
      status: {
        pendente: "Enviando",
        enviado: "Enviado",
        entregue: "Entregue",
        lido: "Lido",
        falhou: "Falha ao enviar",
      },
      reenviar: "Reenviar",
      motivosFalhaEntrega: {
        "131026": "Número não recebe mensagens no WhatsApp",
        "131047": "Fora da janela de 24 horas — só template aprovado",
        "131053": "Formato de arquivo não suportado",
        "132000": "Template com número de parâmetros diferente do aprovado",
        "132001": "Template não existe nesse idioma",
      },
      motivoFalhaNaoInformado: "O provedor não informou o motivo da falha",
      carregarAnteriores: "",
      carregandoAnteriores: "",
    },
    composer: {
      placeholder: "",
      enviar: "",
      anexo: "",
      anexoIndisponivel: "",
      anexoSelecionar: "",
      anexoRemover: "",
      anexoEnviando: "",
      anexoErro: "",
      anexoLegendaPlaceholder: "",
        anexoTipoNaoPermitido: "",
        anexoSoltar: "",
        anexoEnviandoLote: "",
        anexoExcedeuLimite: "",
      emoji: "",
      janelaFechadaTitulo: "",
      janelaFechadaDescricao: "",
      semTemplates: "",
      agendar: "",
      mensagensRapidas: "",
    },
    tempoReal: {
      reconectando: "",
      conversaEncerrada: "",
      transferenciaRecebida: "",
      transferenciaRecebidaDescricao: "",
      abrirTransferencia: "",
    },
    media: { imagem: "", audio: "", documento: "", baixar: "" },
  },
} satisfies ParcialProfundo<Textos>;

function renderComTextos(ui: React.ReactElement) {
  return render(
    <TextosProvider textos={TEXTOS_FIXTURE as Textos}>{ui}</TextosProvider>,
  );
}

describe("StatusEntregaIcone", () => {
  it.each([
    ["PENDENTE", "Enviando"],
    ["ENVIADO", "Enviado"],
    ["ENTREGUE", "Entregue"],
    ["LIDO", "Lido"],
  ] as const)("renderiza o ícone certo para %s", (status, rotulo) => {
    renderComTextos(<StatusEntregaIcone status={status} />);
    expect(screen.getByTitle(rotulo)).toBeInTheDocument();
  });

  it("FALHOU mostra texto de erro e o botão de reenviar, que aciona o callback", async () => {
    const onReenviar = vi.fn();
    renderComTextos(<StatusEntregaIcone status="FALHOU" onReenviar={onReenviar} />);

    expect(screen.getByText("Falha ao enviar")).toBeInTheDocument();
    screen.getByRole("button", { name: "Reenviar" }).click();

    expect(onReenviar).toHaveBeenCalledTimes(1);
  });

  it("FALHOU sem callback não mostra o botão de reenviar", () => {
    renderComTextos(<StatusEntregaIcone status="FALHOU" />);
    expect(screen.queryByRole("button", { name: "Reenviar" })).not.toBeInTheDocument();
  });

  // O balão de saída é sempre bg-primary (azul). Estes quatro testes provam o motivo real do
  // "só fica com um traço": as cores antigas eram pensadas para fundo claro.
  it("LIDO não carrega mais text-primary — era a própria cor do balão, ícone azul sobre balão azul", () => {
    renderComTextos(<StatusEntregaIcone status="LIDO" />);
    const icone = screen.getByTitle("Lido").querySelector("svg");
    expect(icone).not.toBeNull();
    expect(icone).not.toHaveClass("text-primary");
  });

  it("LIDO se distingue de ENTREGUE herdando a MESMA cor do balão, mas sem a opacidade reduzida", () => {
    renderComTextos(<StatusEntregaIcone status="LIDO" />);
    expect(screen.getByTitle("Lido").querySelector("svg")).toHaveClass("text-primary-foreground");
  });

  it("ENTREGUE não define cor própria — herda text-primary-foreground/70 do rodapé do balão", () => {
    renderComTextos(<StatusEntregaIcone status="ENTREGUE" />);
    const icone = screen.getByTitle("Entregue").querySelector("svg");
    expect(icone).not.toHaveClass("text-primary-foreground");
    expect(icone).not.toHaveClass("text-primary");
  });

  it("o <span> externo não força mais text-muted-foreground — herda a cor do rodapé do balão", () => {
    renderComTextos(<StatusEntregaIcone status="ENTREGUE" />);
    expect(screen.getByTitle("Entregue")).not.toHaveClass("text-muted-foreground");
  });

  it("FALHOU usa o token de erro calibrado para superfície escura, não text-destructive", () => {
    const onReenviar = vi.fn();
    renderComTextos(<StatusEntregaIcone status="FALHOU" onReenviar={onReenviar} />);
    const rotulo = screen.getByText("Falha ao enviar").closest("span");
    expect(rotulo).toHaveClass("text-sidebar-item-texto-perigo");
    expect(rotulo).not.toHaveClass("text-destructive");
    expect(screen.getByRole("button", { name: "Reenviar" })).toHaveClass(
      "text-sidebar-item-texto-perigo",
    );
  });
});
