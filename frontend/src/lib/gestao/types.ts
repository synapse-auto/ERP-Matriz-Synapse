/** Contrato de /api/v1/gestao/permissoes (docs/47). Ids de módulo e capacidade são estáveis. */
export type Papel = "ATENDENTE" | "OPERADOR" | "SUBGESTOR" | "GESTOR" | "ADMINISTRADOR";
export type Nivel = "SEM_ACESSO" | "VER" | "EDITAR" | "GERENCIAR";
export type Motivo = "PERMITIDO" | "TETO_DO_PAPEL" | "FLAG_DESLIGADA" | "NIVEL_DO_MODULO" | "DESLIGADO" | "DEPENDENCIA";
export type Origem = "FIXO" | "ESTRUTURAL" | "PERFIL" | "EXCECAO";

export const NIVEIS: readonly Nivel[] = ["SEM_ACESSO", "VER", "EDITAR", "GERENCIAR"];

/**
 * Feature flag de Exceções por usuário (FuncionalidadeDeExcecoes.java). Desligada ou ausente, a aba
 * aparece como "Em breve" e o backend recusa toda gravação de exceção.
 */
export const FLAG_EXCECOES = "gestao_excecoes";

export interface ModuloDoCatalogo {
  id: string;
  nivelMinimoPermitido: Nivel;
  flag: string | null;
  nivelMaximoPorPapel: Record<Papel, Nivel>;
  nivelPadraoPorPapel: Record<Papel, Nivel>;
}

export interface CapacidadeDoCatalogo {
  id: string;
  modulo: string;
  nivelMinimo: Nivel;
  tipo: "ACAO" | "ALCANCE_ESTRUTURAL";
  sensivel: boolean;
  teto: Papel[];
  delegavel: boolean;
  dependencias: string[];
  alcancePorPapel: Partial<Record<Papel, "MEUS" | "TODOS">>;
}

export interface Catalogo {
  modulos: ModuloDoCatalogo[];
  capacidades: CapacidadeDoCatalogo[];
}

export interface LinhaDeModulo {
  id: string;
  doPerfil: Nivel;
  excecao: Nivel | null;
  efetivo: Nivel;
  minimo: Nivel;
  maximo: Nivel;
}

export interface LinhaDeCapacidade {
  id: string;
  doPerfil: boolean | null;
  excecao: boolean | null;
  permitido: boolean;
  motivo: Motivo;
  origem: Origem;
  alcance: "MEUS" | "TODOS" | null;
  alteravel: boolean;
}

export interface Perfil {
  papel: Papel;
  fixo: boolean;
  revisao: number;
  usuarios: number;
  permitidas: number;
  total: number;
  editavel: boolean;
  modulos: LinhaDeModulo[];
  capacidades: LinhaDeCapacidade[];
}

export interface ResumoDeUsuario {
  id: string;
  nome: string;
  email: string;
  papel: Papel;
  ativo: boolean;
  fotoUrl: string | null;
  excecoes: number;
  editavel: boolean;
}

export interface PermissoesDeUsuario {
  usuario: ResumoDeUsuario;
  revisao: number;
  revisaoDoPerfil: number;
  fixo: boolean;
  modulos: LinhaDeModulo[];
  capacidades: LinhaDeCapacidade[];
}

export interface EfetivoDaCapacidade {
  permitido: boolean;
  motivo: Motivo;
  alcance: "MEUS" | "TODOS" | null;
}

export interface MinhasPermissoes {
  usuarioId: string;
  papel: Papel;
  revisao: number;
  acessaGestao: boolean;
  editaPerfis: boolean;
  editaExcecoes: boolean;
  capacidades: Record<string, EfetivoDaCapacidade>;
}

/** Rascunho: o que vai no PUT. Chave ausente = padrão do catálogo (perfil) ou herdar (exceção). */
export interface Rascunho {
  niveis: Record<string, Nivel>;
  acoes: Record<string, boolean>;
}

export interface PreviaDeCopia {
  niveis: Record<string, Nivel>;
  acoes: Record<string, boolean>;
  alteracoes: { chave: string; antes: string; depois: string }[];
  impedidos: { chave: string; motivo: "TETO_DO_PAPEL" | "FORA_DA_ALCADA" }[];
}

export interface Gravacao {
  operacao: string;
  revisaoAnterior: number;
  revisao: number;
  excecoes: number;
}
