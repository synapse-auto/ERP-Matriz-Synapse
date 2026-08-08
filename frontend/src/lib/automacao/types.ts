export type TipoConfiguracaoAutomacao = "INT" | "DECIMAL" | "BOOLEAN" | "TEXT";

export interface ParametroAutomacao {
  chave: string;
  valor: string;
  unidade: string | null;
  tipo: TipoConfiguracaoAutomacao;
  valorMin: number | null;
  valorMax: number | null;
  descricao: string | null;
  atualizadoEm: string;
}
