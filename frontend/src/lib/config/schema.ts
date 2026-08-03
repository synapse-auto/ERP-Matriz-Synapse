import { z } from "zod";

/**
 * Espelha tema.json (backend/crm-app/src/main/resources/tema.json), gerado a partir de
 * design/TOKENS.md. Nenhum campo tem default aqui: se o backend não devolver um token, a falha deve
 * aparecer (schema inválido), não virar uma cor arbitrária escolhida em runtime.
 */
export const TemaSchema = z.object({
  corPrimaria: z.string(),
  corPrimariaHover: z.string(),
  corPrimariaSuave: z.string(),
  corPrimariaBorda: z.string(),
  corPrimariaTexto: z.string(),

  fundoApp: z.string(),
  fundoSuperficie: z.string(),
  fundoSutil: z.string(),
  fundoSidebar: z.string(),
  fundoSidebarBloco: z.string(),

  textoForte: z.string(),
  textoPadrao: z.string(),
  textoSuave: z.string(),
  textoFraco: z.string(),
  textoSidebarTitulo: z.string(),
  textoSidebarSub: z.string(),
  textoSidebarItem: z.string(),

  borda: z.string(),
  bordaForte: z.string(),
  bordaSuave: z.string(),

  corSucesso: z.string(),
  corAtencao: z.string(),
  corAtencaoEscura: z.string(),
  corIa: z.string(),
  corInfo: z.string(),
  corDestaque2: z.string(),
  corDestaque3: z.string(),
  corErro: z.string(),
  corErroSuave: z.string(),

  fonteBase: z.string(),
  fonteMono: z.string(),

  textoXs: z.string(),
  textoSm: z.string(),
  textoBase: z.string(),
  textoMd: z.string(),
  textoLg: z.string(),
  textoXl: z.string(),
  texto2xl: z.string(),

  raioSm: z.string(),
  raioMd: z.string(),
  raioLg: z.string(),
  raioXl: z.string(),
  raioPill: z.string(),

  sombraSm: z.string(),
  sombraMd: z.string(),
  sombraLg: z.string(),
  sombraXl: z.string(),
  sombraPrimaria: z.string(),

  logoUrl: z.string().nullable(),
});

export type Tema = z.infer<typeof TemaSchema>;

/** Espelha textos.json — catálogo de strings de UI. Cresce conforme novas telas nascem. */
export const TextosSchema = z.object({
  app: z.object({
    nome: z.string(),
    marca: z.string(),
    subtitulo: z.string(),
  }),
  menu: z.object({
    grupoMenu: z.string(),
    grupoGestao: z.string(),
    itens: z.record(z.string(), z.string()),
  }),
  rodape: z.object({
    trocarConta: z.string(),
    sair: z.string(),
  }),
  login: z.object({
    titulo: z.string(),
    subtitulo: z.string(),
    campoEmail: z.string(),
    campoSenha: z.string(),
    botaoEntrar: z.string(),
    entrando: z.string(),
    erroCredenciais: z.string(),
    erroGenerico: z.string(),
  }),
  estados: z.object({
    carregando: z.string(),
    vazio: z.string(),
    erroGenerico: z.string(),
    tentarNovamente: z.string(),
    emConstrucao: z.string(),
    sessaoExpirada: z.string(),
  }),
  atendimentos: z.object({
    visoes: z.object({
      ativos: z.string(),
      pendentes: z.string(),
      potenciais: z.string(),
      todos: z.string(),
    }),
    filtros: z.object({
      etapa: z.string(),
      status: z.string(),
      tag: z.string(),
      atendente: z.string(),
      busca: z.string(),
    }),
    cartao: z.object({
      semAtendente: z.string(),
      vazio: z.string(),
    }),
    cabecalho: z.object({
      atendidoPor: z.string(),
      semAtendente: z.string(),
      transferir: z.string(),
      finalizar: z.string(),
      buscar: z.string(),
    }),
    transferir: z.object({
      titulo: z.string(),
      descricao: z.string(),
      devolverParaIa: z.string(),
      assumirParaMim: z.string(),
      confirmar: z.string(),
      cancelar: z.string(),
      sucesso: z.string(),
      erro: z.string(),
    }),
    finalizar: z.object({
      titulo: z.string(),
      descricao: z.string(),
      confirmar: z.string(),
      cancelar: z.string(),
      sucesso: z.string(),
      erro: z.string(),
    }),
    mensagem: z.object({
      status: z.object({
        pendente: z.string(),
        enviado: z.string(),
        entregue: z.string(),
        lido: z.string(),
        falhou: z.string(),
      }),
      reenviar: z.string(),
    }),
    composer: z.object({
      placeholder: z.string(),
      enviar: z.string(),
      anexo: z.string(),
      anexoIndisponivel: z.string(),
      emoji: z.string(),
      janelaFechadaTitulo: z.string(),
      janelaFechadaDescricao: z.string(),
      semTemplates: z.string(),
    }),
    tempoReal: z.object({
      reconectando: z.string(),
      conversaEncerrada: z.string(),
    }),
    media: z.object({
      imagem: z.string(),
      audio: z.string(),
      documento: z.string(),
      baixar: z.string(),
    }),
  }),
});

export type Textos = z.infer<typeof TextosSchema>;
