import type { CitacaoMensagem, MensagemResposta, OrigemDaCitacao } from "./types";

const LIMITE_PREVIA = 120;

type RotulosDeMidia = {
  imagem: string;
  audio: string;
  documento: string;
  video?: string;
  localizacao?: string;
  origemIndisponivel: string;
};

/** Citação otimista da resposta — o servidor persiste a mesma forma sanitizada. */
export function citacaoDeResposta(mensagem: MensagemResposta): CitacaoMensagem {
  return {
    origemId: mensagem.id,
    tipoReferencia: "RESPOSTA",
    autor: autorDaMensagem(mensagem),
    tipoConteudo: mensagem.tipo,
    previa: previaBruta(mensagem),
  };
}

export function previaExibida(citacao: CitacaoMensagem, rotulos: RotulosDeMidia): string {
  const previa = citacao.previa.trim();
  if (previa) return previa;
  if (citacao.tipoConteudo === "IMAGEM") return rotulos.imagem;
  if (citacao.tipoConteudo === "AUDIO") return rotulos.audio;
  if (citacao.tipoConteudo === "DOCUMENTO") return rotulos.documento;
  if (citacao.tipoConteudo === "VIDEO" && rotulos.video) return rotulos.video;
  if (citacao.tipoConteudo === "LOCALIZACAO" && rotulos.localizacao) return rotulos.localizacao;
  return rotulos.origemIndisponivel;
}

export function origemDaMensagem(mensagem: MensagemResposta): OrigemDaCitacao {
  return {
    id: mensagem.id,
    tipo: mensagem.tipo,
    conteudo: mensagem.conteudo,
    midiaUrl: mensagem.midiaUrl,
    midiaMetadados: mensagem.midiaMetadados,
    removida: false,
  };
}

function autorDaMensagem(mensagem: MensagemResposta): string {
  const nome = mensagem.remetenteNome?.trim();
  if (nome) return nome;
  switch (mensagem.remetenteTipo) {
    case "LEAD":
      return "Lead";
    case "IA":
      return "IA";
    case "SISTEMA":
      return "Sistema";
    default:
      return "Atendente";
  }
}

function previaBruta(mensagem: MensagemResposta): string {
  const texto = mensagem.conteudo?.trim();
  if (texto) return sanitizar(texto);
  if (!mensagem.midiaMetadados) return "";
  try {
    const metadados = JSON.parse(mensagem.midiaMetadados) as { legenda?: string; nome?: string };
    return sanitizar(metadados.legenda || metadados.nome || "");
  } catch {
    return "";
  }
}

function sanitizar(bruto: string): string {
  const compacto = bruto.replace(/[\n\r\t]+/g, " ").replace(/ +/g, " ").trim();
  return compacto.length <= LIMITE_PREVIA ? compacto : compacto.slice(0, LIMITE_PREVIA);
}
