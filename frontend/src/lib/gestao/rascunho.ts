/**
 * Rascunho de permissões no navegador. Só apresenta e prepara o PUT: a decisão de verdade é do
 * backend, que recalcula e valida tudo de novo. A simulação espelha PoliticaDePermissoes.java para
 * a tela nunca mostrar ligado o que está bloqueado antes mesmo de salvar.
 */
import type {
  Catalogo,
  CapacidadeDoCatalogo,
  LinhaDeCapacidade,
  LinhaDeModulo,
  Motivo,
  Nivel,
  Origem,
  Papel,
  Rascunho,
} from "./types";
import { NIVEIS } from "./types";

export const RASCUNHO_VAZIO: Rascunho = { niveis: {}, acoes: {} };

export function ordem(nivel: Nivel): number {
  return NIVEIS.indexOf(nivel);
}

export function perfilFixo(papel: Papel): boolean {
  return papel === "GESTOR" || papel === "ADMINISTRADOR";
}

export function configuravel(c: CapacidadeDoCatalogo, papel: Papel): boolean {
  return c.tipo === "ACAO" && c.teto.includes(papel);
}

/** Perfil completo lido do servidor (valores explícitos de tudo que é configurável). */
export function rascunhoDoPerfil(modulos: LinhaDeModulo[], capacidades: LinhaDeCapacidade[]): Rascunho {
  const niveis: Record<string, Nivel> = {};
  modulos.forEach((m) => (niveis[m.id] = m.doPerfil));
  const acoes: Record<string, boolean> = {};
  capacidades.forEach((c) => {
    if (c.doPerfil != null) acoes[c.id] = c.doPerfil;
  });
  return { niveis, acoes };
}

/** Só as exceções explícitas do usuário. */
export function rascunhoDasExcecoes(modulos: LinhaDeModulo[], capacidades: LinhaDeCapacidade[]): Rascunho {
  const niveis: Record<string, Nivel> = {};
  modulos.forEach((m) => {
    if (m.excecao != null) niveis[m.id] = m.excecao;
  });
  const acoes: Record<string, boolean> = {};
  capacidades.forEach((c) => {
    if (c.excecao != null) acoes[c.id] = c.excecao;
  });
  return { niveis, acoes };
}

export function quantidade(rascunho: Rascunho): number {
  return Object.keys(rascunho.niveis).length + Object.keys(rascunho.acoes).length;
}

export function iguais(a: Rascunho, b: Rascunho): boolean {
  return chavesAlteradas(a, b).length === 0;
}

/** Chaves (modulo:x ou id de capacidade) cujo valor difere entre dois rascunhos. */
export function chavesAlteradas(salvo: Rascunho, atual: Rascunho): string[] {
  const chaves = new Set<string>();
  for (const id of new Set([...Object.keys(salvo.niveis), ...Object.keys(atual.niveis)])) {
    if (salvo.niveis[id] !== atual.niveis[id]) chaves.add(`modulo:${id}`);
  }
  for (const id of new Set([...Object.keys(salvo.acoes), ...Object.keys(atual.acoes)])) {
    if (salvo.acoes[id] !== atual.acoes[id]) chaves.add(id);
  }
  return [...chaves];
}

function limites(catalogo: Catalogo, moduloId: string, papel: Papel): { minimo: Nivel; maximo: Nivel } {
  const modulo = catalogo.modulos.find((m) => m.id === moduloId);
  return {
    minimo: modulo?.nivelMinimoPermitido ?? "SEM_ACESSO",
    maximo: modulo?.nivelMaximoPorPapel[papel] ?? "SEM_ACESSO",
  };
}

/**
 * Preset de nível. Modo perfil: grava o nível e liga/desliga as ações pela régua. Modo exceção
 * (com `perfil`): grava só o que difere do perfil — igual ao perfil volta a herdar.
 */
export function aplicarNivel(
  rascunho: Rascunho,
  catalogo: Catalogo,
  papel: Papel,
  moduloId: string,
  nivel: Nivel,
  perfil?: Rascunho,
): Rascunho {
  const niveis = { ...rascunho.niveis };
  const acoes = { ...rascunho.acoes };
  if (perfil && perfil.niveis[moduloId] === nivel) delete niveis[moduloId];
  else niveis[moduloId] = nivel;
  catalogo.capacidades
    .filter((c) => c.modulo === moduloId && configuravel(c, papel))
    .forEach((c) => {
      const desejado = ordem(c.nivelMinimo) <= ordem(nivel);
      if (perfil && (perfil.acoes[c.id] ?? false) === desejado) delete acoes[c.id];
      else acoes[c.id] = desejado;
    });
  return { niveis, acoes };
}

export function alternarAcao(rascunho: Rascunho, id: string, valor: boolean, perfil?: Rascunho): Rascunho {
  const acoes = { ...rascunho.acoes };
  if (perfil && (perfil.acoes[id] ?? false) === valor) delete acoes[id];
  else acoes[id] = valor;
  return { niveis: rascunho.niveis, acoes };
}

export function restaurarAcao(rascunho: Rascunho, id: string): Rascunho {
  const acoes = { ...rascunho.acoes };
  delete acoes[id];
  return { niveis: rascunho.niveis, acoes };
}

export interface EstadoSimulado {
  permitido: boolean;
  motivo: Motivo;
  origem: Origem;
}

export interface Simulacao {
  niveis: Record<string, Nivel>;
  estados: Record<string, EstadoSimulado>;
}

/** Espelho de PoliticaDePermissoes.calcular para o rascunho corrente. */
export function simular(catalogo: Catalogo, papel: Papel, perfil: Rascunho, excecoes: Rascunho = RASCUNHO_VAZIO): Simulacao {
  const niveis: Record<string, Nivel> = {};
  for (const m of catalogo.modulos) {
    const { minimo, maximo } = limites(catalogo, m.id, papel);
    let nivel = excecoes.niveis[m.id] ?? perfil.niveis[m.id] ?? m.nivelPadraoPorPapel[papel];
    if (ordem(nivel) > ordem(maximo)) nivel = maximo;
    if (ordem(nivel) < ordem(minimo)) nivel = minimo;
    niveis[m.id] = nivel;
  }
  const estados: Record<string, EstadoSimulado> = {};
  const fixo = perfilFixo(papel);
  for (const c of catalogo.capacidades) {
    const origemBase: Origem = fixo ? "FIXO" : "PERFIL";
    if (!c.teto.includes(papel)) {
      estados[c.id] = { permitido: false, motivo: "TETO_DO_PAPEL", origem: origemBase };
      continue;
    }
    if (c.tipo === "ALCANCE_ESTRUTURAL") {
      estados[c.id] = { permitido: true, motivo: "PERMITIDO", origem: "ESTRUTURAL" };
      continue;
    }
    const origem: Origem = fixo
      ? "FIXO"
      : c.id in excecoes.acoes || c.modulo in excecoes.niveis ? "EXCECAO" : "PERFIL";
    if (!fixo && ordem(niveis[c.modulo]) < ordem(c.nivelMinimo)) {
      estados[c.id] = { permitido: false, motivo: "NIVEL_DO_MODULO", origem };
      continue;
    }
    const ligado = fixo || (excecoes.acoes[c.id] ?? perfil.acoes[c.id] ?? false);
    if (!ligado) {
      estados[c.id] = { permitido: false, motivo: "DESLIGADO", origem };
      continue;
    }
    const dependenciaNegada = c.dependencias.some((d) => estados[d] && !estados[d].permitido);
    estados[c.id] = dependenciaNegada
      ? { permitido: false, motivo: "DEPENDENCIA", origem }
      : { permitido: true, motivo: "PERMITIDO", origem };
  }
  return { niveis, estados };
}

/** Um lado da comparação de alçada: o que vai gravado e o efetivo que isso produz. */
export interface EstadoDaEdicao {
  acoes: Record<string, boolean>;
  simulacao: Simulacao;
}

export interface ViolacaoDaDelegacao {
  capacidade: string;
  motivo: "FORA_DO_CONJUNTO_DELEGAVEL" | "ACIMA_DA_PROPRIA_PERMISSAO";
}

/**
 * Espelho de PoliticaDeConcessao.exigirDentroDaDelegacao para o SUBGESTOR delegado — o backend
 * revalida ao salvar. Toda ação cujo interruptor gravado OU efetivo muda precisa ser delegável e não
 * pode ir para ligado se o ator não a tem. Nível de módulo e dependência entram pelo efetivo: é isso
 * que deixa o nível editável sem virar atalho para liberar o que o interruptor não liberaria.
 */
export function violacaoDaDelegacao(
  catalogo: Catalogo,
  atorTem: (capacidade: string) => boolean,
  antes: EstadoDaEdicao,
  depois: EstadoDaEdicao,
): ViolacaoDaDelegacao | null {
  for (const c of catalogo.capacidades) {
    if (c.tipo !== "ACAO") continue;
    const exigir = (paraLigado: boolean): ViolacaoDaDelegacao | null => {
      if (!c.delegavel) return { capacidade: c.id, motivo: "FORA_DO_CONJUNTO_DELEGAVEL" };
      if (paraLigado && !atorTem(c.id)) return { capacidade: c.id, motivo: "ACIMA_DA_PROPRIA_PERMISSAO" };
      return null;
    };
    const gravado = antes.acoes[c.id] !== depois.acoes[c.id] ? exigir(depois.acoes[c.id] === true) : null;
    if (gravado) return gravado;
    const efetivoDepois = depois.simulacao.estados[c.id]?.permitido ?? false;
    const efetivo = (antes.simulacao.estados[c.id]?.permitido ?? false) !== efetivoDepois ? exigir(efetivoDepois) : null;
    if (efetivo) return efetivo;
  }
  return null;
}

/** "N de M": só ações configuráveis no teto do papel. */
export function contagem(catalogo: Catalogo, papel: Papel, simulacao: Simulacao): { permitidas: number; total: number } {
  const configuraveis = catalogo.capacidades.filter((c) => configuravel(c, papel));
  return {
    permitidas: configuraveis.filter((c) => simulacao.estados[c.id]?.permitido).length,
    total: configuraveis.length,
  };
}

/** Alguma alteração pendente toca ação sensível ou nível de módulo com ação sensível? */
export function tocaSensivel(catalogo: Catalogo, chaves: string[]): boolean {
  return chaves.some((chave) => {
    if (chave.startsWith("modulo:")) {
      const modulo = chave.slice("modulo:".length);
      return catalogo.capacidades.some((c) => c.modulo === modulo && c.sensivel);
    }
    return catalogo.capacidades.find((c) => c.id === chave)?.sensivel ?? false;
  });
}

/** Busca por módulo ou ação (rótulo e id), sem acento e sem caixa. */
export function normalizar(texto: string): string {
  return texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
}

/** Remove só a exceção de nível do módulo (as ações continuam como estão no rascunho). */
export function restaurarNivel(rascunho: Rascunho, moduloId: string): Rascunho {
  const niveis = { ...rascunho.niveis };
  delete niveis[moduloId];
  return { niveis, acoes: rascunho.acoes };
}
