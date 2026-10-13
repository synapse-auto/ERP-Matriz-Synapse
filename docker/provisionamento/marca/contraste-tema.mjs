// Contraste e validação estrutural de um tema (tema.json) da instância.
//
// Sem dependências: roda em Node >= 18, no script de aplicação e nos testes do frontend. A razão de
// contraste segue a fórmula do WCAG 2.x (luminância relativa com linearização sRGB), calculada e não
// aproximada. Camadas translúcidas (overlays da sidebar) são compostas sobre o fundo antes da razão.

/** Chaves que o tema da Disbrel pode ter além das do tema.json padrão. */
export const CHAVES_OPCIONAIS_ALEM_DO_PADRAO = ["sidebarTextoForte", "mensagemFalhaTexto"];

/** Valor que o frontend usa quando o tema não traz `sidebarTextoForte` (comportamento histórico). */
export const SIDEBAR_TEXTO_FORTE_PADRAO = "#FFFFFF";

const HEX = /^#([0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;
const FUNCAO = /^rgba?\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*(?:,\s*([0-9.]+)\s*)?\)$/i;

/** @returns {{r:number,g:number,b:number,a:number}|null} canais 0-255 e alfa 0-1; null se não for cor. */
export function parseCor(texto) {
  if (typeof texto !== "string") return null;
  const valor = texto.trim();
  const hex = HEX.exec(valor);
  if (hex) {
    let h = hex[1];
    if (h.length <= 4) h = [...h].map((c) => c + c).join("");
    const n = (i) => parseInt(h.slice(i, i + 2), 16);
    return { r: n(0), g: n(2), b: n(4), a: h.length === 8 ? n(6) / 255 : 1 };
  }
  const fn = FUNCAO.exec(valor);
  if (fn) {
    const [r, g, b] = [fn[1], fn[2], fn[3]].map(Number);
    const a = fn[4] === undefined ? 1 : Number(fn[4]);
    if ([r, g, b].some((c) => c < 0 || c > 255) || a < 0 || a > 1) return null;
    return { r, g, b, a };
  }
  return null;
}

/** Compõe `frente` (com alfa) sobre `fundo` opaco. */
export function compor(frente, fundo) {
  const a = frente.a;
  return {
    r: frente.r * a + fundo.r * (1 - a),
    g: frente.g * a + fundo.g * (1 - a),
    b: frente.b * a + fundo.b * (1 - a),
    a: 1,
  };
}

function linear(canal) {
  const c = canal / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function luminancia({ r, g, b }) {
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

/** Razão de contraste WCAG entre duas cores opacas. */
export function razao(a, b) {
  const la = luminancia(a);
  const lb = luminancia(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

function lab({ r, g, b }) {
  const [R, G, B] = [r, g, b].map(linear);
  const x = (0.4124564 * R + 0.3575761 * G + 0.1804375 * B) / 0.95047;
  const y = 0.2126729 * R + 0.7151522 * G + 0.072175 * B;
  const z = (0.0193339 * R + 0.119192 * G + 0.9503041 * B) / 1.08883;
  const f = (t) => (t > 216 / 24389 ? Math.cbrt(t) : (24389 / 27 * t + 16) / 116);
  const [fx, fy, fz] = [f(x), f(y), f(z)];
  return { L: 116 * fy - 16, a: 500 * (fx - fy), b: 200 * (fy - fz) };
}

/** Distância CIE76 (euclidiana em Lab) entre duas cores opacas; só informativa. */
export function distanciaDeCor(a, b) {
  const [p, q] = [lab(a), lab(b)];
  return Math.hypot(p.L - q.L, p.a - q.a, p.b - q.b);
}

/**
 * Pares que a interface realmente usa. `fundo` é uma lista de camadas, da base para o topo; uma
 * camada translúcida é composta sobre a anterior. `minimo`: 4,5 para texto normal e 3 para texto
 * grande e componentes de interface (ícone, barra de item ativo).
 */
export const PARES = [
  { id: "C1", grupo: "Conteúdo", uso: "Texto forte sobre o cartão", texto: "textoForte", fundo: ["fundoSuperficie"], minimo: 4.5 },
  { id: "C2", grupo: "Conteúdo", uso: "Texto forte sobre o fundo do app", texto: "textoForte", fundo: ["fundoApp"], minimo: 4.5 },
  { id: "C3", grupo: "Conteúdo", uso: "Texto padrão sobre o cartão", texto: "textoPadrao", fundo: ["fundoSuperficie"], minimo: 4.5 },
  { id: "C4", grupo: "Conteúdo", uso: "Texto padrão sobre o fundo do app", texto: "textoPadrao", fundo: ["fundoApp"], minimo: 4.5 },
  { id: "C5", grupo: "Conteúdo", uso: "Texto padrão sobre fundo sutil", texto: "textoPadrao", fundo: ["fundoSutil"], minimo: 4.5 },
  { id: "C6", grupo: "Conteúdo", uso: "Texto suave sobre o cartão", texto: "textoSuave", fundo: ["fundoSuperficie"], minimo: 4.5 },
  { id: "C7", grupo: "Conteúdo", uso: "Texto suave sobre o fundo do app", texto: "textoSuave", fundo: ["fundoApp"], minimo: 4.5 },
  { id: "C8", grupo: "Conteúdo", uso: "Texto suave sobre fundo sutil", texto: "textoSuave", fundo: ["fundoSutil"], minimo: 4.5 },
  { id: "C9", grupo: "Conteúdo", uso: "Texto fraco (placeholder, auxiliar) sobre o cartão", texto: "textoFraco", fundo: ["fundoSuperficie"], minimo: 3 },
  { id: "P1", grupo: "Ação principal", uso: "Texto do botão primário", texto: "corPrimariaTexto", fundo: ["corPrimaria"], minimo: 4.5 },
  { id: "P2", grupo: "Ação principal", uso: "Texto do botão primário em hover", texto: "corPrimariaTexto", fundo: ["corPrimariaHover"], minimo: 4.5 },
  { id: "P3", grupo: "Ação principal", uso: "Link e destaque primário sobre o cartão", texto: "corPrimaria", fundo: ["fundoSuperficie"], minimo: 4.5 },
  { id: "P4", grupo: "Ação principal", uso: "Primário sobre o fundo suave (accent)", texto: "corPrimaria", fundo: ["corPrimariaSuave"], minimo: 4.5 },
  { id: "P5", grupo: "Ação principal", uso: "Primário sobre o fundo do app", texto: "corPrimaria", fundo: ["fundoApp"], minimo: 4.5 },
  { id: "M1", grupo: "Balão de saída", uso: "Falha de entrega sobre o balão de saída", texto: "mensagemFalhaTexto", fundo: ["corPrimaria"], minimo: 4.5 },
  { id: "M2", grupo: "Balão de saída", uso: "Marca de lida (ícone) sobre o balão de saída", texto: "mensagemLidaTexto", fundo: ["corPrimaria"], minimo: 3 },
  { id: "E1", grupo: "Erro", uso: "Texto de erro sobre o cartão", texto: "corErro", fundo: ["fundoSuperficie"], minimo: 4.5 },
  { id: "E2", grupo: "Erro", uso: "Texto de erro sobre o fundo de erro suave", texto: "corErro", fundo: ["corErroSuave"], minimo: 4.5 },
  { id: "S1", grupo: "Sidebar", uso: "Item em repouso", texto: "textoSidebarItem", fundo: ["fundoSidebar"], minimo: 4.5 },
  { id: "S2", grupo: "Sidebar", uso: "Item em hover", texto: "sidebarItemTextoHover", fundo: ["fundoSidebar", "sidebarItemOverlayHover"], minimo: 4.5 },
  { id: "S3", grupo: "Sidebar", uso: "Item ativo (texto)", texto: "sidebarTextoForte", fundo: ["fundoSidebar", "sidebarItemOverlayAtivo"], minimo: 4.5 },
  { id: "S4", grupo: "Sidebar", uso: "Item ativo (ícone)", texto: "sidebarItemIconeAtivo", fundo: ["fundoSidebar", "sidebarItemOverlayAtivo"], minimo: 3 },
  { id: "S5", grupo: "Sidebar", uso: "Item ativo (barra lateral)", texto: "sidebarItemAcentoAtivo", fundo: ["fundoSidebar"], minimo: 3 },
  { id: "S6", grupo: "Sidebar", uso: "Título de grupo do menu", texto: "textoSidebarTitulo", fundo: ["fundoSidebar"], minimo: 4.5 },
  { id: "S7", grupo: "Sidebar", uso: "Subtítulo da marca", texto: "textoSidebarSub", fundo: ["fundoSidebar"], minimo: 4.5 },
  { id: "S8", grupo: "Sidebar", uso: "Nome da marca e do usuário", texto: "sidebarTextoForte", fundo: ["fundoSidebar"], minimo: 4.5 },
  { id: "S9", grupo: "Sidebar", uso: "Item de perigo (Sair), repouso", texto: "sidebarItemTextoPerigo", fundo: ["fundoSidebar"], minimo: 4.5 },
  { id: "S10", grupo: "Sidebar", uso: "Item de perigo (Sair), hover", texto: "sidebarItemTextoPerigo", fundo: ["fundoSidebar", "sidebarItemOverlayPerigo"], minimo: 4.5 },
  { id: "S11", grupo: "Sidebar", uso: "Menu do usuário (item em repouso)", texto: "textoSidebarItem", fundo: ["fundoSidebarBloco"], minimo: 4.5 },
  { id: "S12", grupo: "Sidebar", uso: "Menu do usuário (item ativo)", texto: "sidebarTextoForte", fundo: ["fundoSidebarBloco", "sidebarItemOverlayAtivo"], minimo: 4.5 },
];

function cor(tema, chave) {
  const valor = tema[chave] ?? (chave === "sidebarTextoForte" ? SIDEBAR_TEXTO_FORTE_PADRAO : undefined);
  return parseCor(valor);
}

/** Tokens opcionais que o frontend deriva de outros quando ausentes (espelha o TemaSchema). */
function corComFallback(tema, chave) {
  const direta = cor(tema, chave);
  if (direta) return direta;
  const derivados = {
    sidebarItemTextoHover: "textoSidebarItem",
    sidebarItemIconeAtivo: "corPrimaria",
    sidebarItemOverlayHover: "fundoSidebarBloco",
    sidebarItemOverlayAtivo: "fundoSidebarBloco",
    sidebarItemAcentoAtivo: "corPrimaria",
    sidebarItemTextoPerigo: "corErro",
    sidebarItemOverlayPerigo: "fundoSidebarBloco",
    mensagemFalhaTexto: "sidebarItemTextoPerigo",
    mensagemLidaTexto: "corSucesso",
  };
  return derivados[chave] ? corComFallback(tema, derivados[chave]) : null;
}

/** Cor opaca resultante de empilhar as camadas, base primeiro. null se alguma camada faltar. */
export function resolverFundo(tema, camadas) {
  let atual = null;
  for (const chave of camadas) {
    const camada = corComFallback(tema, chave);
    if (!camada) return null;
    atual = atual === null ? { ...camada, a: 1 } : compor(camada, atual);
  }
  return atual;
}

/** Uma linha por par: razão calculada, mínimo exigido e se passa. */
export function calcularContraste(tema) {
  return PARES.map((par) => {
    const fundo = resolverFundo(tema, par.fundo);
    const texto = corComFallback(tema, par.texto);
    if (!fundo || !texto) {
      return { ...par, razao: null, ok: false, motivo: "cor ausente ou inválida" };
    }
    const textoOpaco = texto.a < 1 ? compor(texto, fundo) : texto;
    const valor = razao(textoOpaco, fundo);
    return { ...par, razao: valor, ok: valor >= par.minimo };
  });
}

/** Distância entre primário e erro, para avaliar se a ação principal se confunde com o perigo. */
export function distanciaPrimarioErro(tema) {
  const primaria = corComFallback(tema, "corPrimaria");
  const erro = corComFallback(tema, "corErro");
  if (!primaria || !erro) return null;
  return { deltaE: distanciaDeCor(primaria, erro), razaoDeLuminancia: razao(primaria, erro) };
}

/**
 * Validação estrutural: mesmas chaves do tema padrão (mais as opcionais declaradas), valores do tipo
 * certo e cores legíveis. Devolve a lista de problemas; vazia = válido.
 */
export function validarEstrutura(tema, padrao) {
  const problemas = [];
  if (tema === null || typeof tema !== "object" || Array.isArray(tema)) {
    return ["o tema deve ser um objeto JSON"];
  }
  const esperadas = new Set([...Object.keys(padrao), ...CHAVES_OPCIONAIS_ALEM_DO_PADRAO]);
  for (const chave of Object.keys(padrao)) {
    if (!(chave in tema)) problemas.push(`chave ausente: ${chave}`);
  }
  for (const chave of Object.keys(tema)) {
    if (!esperadas.has(chave)) problemas.push(`chave desconhecida: ${chave}`);
  }
  for (const [chave, valorPadrao] of Object.entries(padrao)) {
    if (!(chave in tema)) continue;
    const valor = tema[chave];
    if (typeof valor !== "string" || valor.trim() === "") {
      problemas.push(`${chave}: deve ser texto não vazio`);
    } else if (parseCor(valorPadrao) && !parseCor(valor)) {
      problemas.push(`${chave}: "${valor}" não é uma cor (#rgb, #rrggbb, rgb() ou rgba())`);
    }
  }
  for (const chave of CHAVES_OPCIONAIS_ALEM_DO_PADRAO) {
    if (chave in tema && !parseCor(tema[chave])) problemas.push(`${chave}: "${tema[chave]}" não é uma cor`);
  }
  return problemas;
}

/** Estrutura + contraste. Cada falha de contraste vira um problema com o valor calculado. */
export function validarTema(tema, padrao) {
  const problemas = validarEstrutura(tema, padrao);
  if (problemas.length > 0) return problemas;
  for (const linha of calcularContraste(tema)) {
    if (!linha.ok) {
      const medido = linha.razao === null ? linha.motivo : `${linha.razao.toFixed(2)}:1`;
      problemas.push(`contraste ${linha.id} (${linha.uso}): ${medido}, mínimo ${linha.minimo}:1`);
    }
  }
  return problemas;
}

/** Tabela em texto: razão calculada de cada par, no tema avaliado e (opcional) no tema de referência. */
export function tabelaDeContraste(tema, referencia) {
  const atual = calcularContraste(tema);
  const base = referencia ? calcularContraste(referencia) : null;
  const linhas = atual.map((linha, i) => {
    const valor = linha.razao === null ? "—" : linha.razao.toFixed(2);
    const ref = base ? ` | ${base[i].razao === null ? "—" : base[i].razao.toFixed(2)}` : "";
    return `| ${linha.id} | ${linha.grupo} | ${linha.uso} | ${linha.minimo} | ${valor} | ${linha.ok ? "ok" : "FALHA"}${ref} |`;
  });
  const cabecalho = base
    ? "| Par | Grupo | Uso | Mínimo | Calculado | Resultado | Padrão (referência) |\n|---|---|---|---:|---:|---|---:|"
    : "| Par | Grupo | Uso | Mínimo | Calculado | Resultado |\n|---|---|---|---:|---:|---|";
  return `${cabecalho}\n${linhas.join("\n")}`;
}
