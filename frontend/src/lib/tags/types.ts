export interface Tag {
  id: string;
  nome: string;
  cor: string;
  icone: string | null;
}

export interface DadosDeTag {
  nome: string;
  cor: string;
  icone: string | null;
}

/** Espelha TagController.ContagemDeTagResposta — item de AgregacaoDeTagsResposta.porTag. */
export interface ContagemDeTag {
  id: string;
  nome: string;
  cor: string;
  icone: string | null;
  quantidade: number;
}

/** Espelha TagController.AgregacaoDeTagsResposta — GET /api/v1/tags/agregacao. */
export interface AgregacaoDeTags {
  totalLeadsVisiveis: number;
  leadsComTag: number;
  percentualTagueados: number;
  tagMaisUsada: ContagemDeTag | null;
  porTag: ContagemDeTag[];
}
