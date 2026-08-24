import { Tema, TemaSchema, Textos, TextosSchema } from "./schema";
import { obterUrlApiServidor } from "@/lib/api/server-api-url";

/**
 * `/api/v1/config/tema` e `/api/v1/config/textos` são públicos (SecurityConfig, E10). O catálogo
 * atende também o login antes da sessão; o tema é buscado somente pelo shell, pois a identidade da
 * tela de login é fixa da Synapse. `cache: "no-store"` mantém "trocar tema.json muda a aparência"
 * verdadeiro para o CRM, sem rebuild nem invalidação manual de cache.
 */
async function buscarConfig<T>(caminho: string): Promise<unknown> {
  const resposta = await fetch(`${obterUrlApiServidor()}${caminho}`, { cache: "no-store" });
  if (!resposta.ok) {
    throw new Error(`Falha ao buscar ${caminho}: HTTP ${resposta.status}`);
  }
  return (await resposta.json()) as T;
}

export async function buscarTema(): Promise<Tema> {
  return TemaSchema.parse(await buscarConfig("/api/v1/config/tema"));
}

export async function buscarTextos(): Promise<Textos> {
  return TextosSchema.parse(await buscarConfig("/api/v1/config/textos"));
}

/**
 * Chaves do tema (camelCase, formato JSON) → nomes de CSS custom property (kebab-case), mais o
 * alias para as variáveis que o shadcn/ui já espera (`--primary`, `--background` etc.) — é isso
 * que faz os componentes shadcn mudarem de aparência SEM edição, só trocando tema.json.
 */
export function temaParaCssVariaveis(tema: Tema): string {
  const declaracoes: string[] = [
    // Tokens Synapse — nomeação própria, usada por componentes fora do shadcn (Sidebar).
    `--cor-primaria: ${tema.corPrimaria}`,
    `--cor-primaria-hover: ${tema.corPrimariaHover}`,
    `--cor-primaria-suave: ${tema.corPrimariaSuave}`,
    `--cor-primaria-borda: ${tema.corPrimariaBorda}`,
    `--cor-primaria-texto: ${tema.corPrimariaTexto}`,
    `--fundo-app: ${tema.fundoApp}`,
    `--fundo-canvas: ${tema.fundoCanvas}`,
    `--fundo-superficie: ${tema.fundoSuperficie}`,
    `--fundo-sutil: ${tema.fundoSutil}`,
    `--fundo-sidebar: ${tema.fundoSidebar}`,
    `--fundo-sidebar-bloco: ${tema.fundoSidebarBloco}`,
    `--texto-forte: ${tema.textoForte}`,
    `--texto-padrao: ${tema.textoPadrao}`,
    `--texto-suave: ${tema.textoSuave}`,
    `--texto-fraco: ${tema.textoFraco}`,
    `--texto-sidebar-titulo: ${tema.textoSidebarTitulo}`,
    `--texto-sidebar-sub: ${tema.textoSidebarSub}`,
    `--texto-sidebar-item: ${tema.textoSidebarItem}`,
    `--sidebar-item-texto-hover: ${tema.sidebarItemTextoHover}`,
    `--sidebar-item-icone-ativo: ${tema.sidebarItemIconeAtivo}`,
    `--sidebar-item-overlay-hover: ${tema.sidebarItemOverlayHover}`,
    `--sidebar-item-overlay-ativo: ${tema.sidebarItemOverlayAtivo}`,
    `--sidebar-item-overlay-ativo-hover: ${tema.sidebarItemOverlayAtivoHover}`,
    `--sidebar-item-acento-ativo: ${tema.sidebarItemAcentoAtivo}`,
    `--marca-icone-gradiente-inicio: ${tema.marcaIconeGradienteInicio}`,
    `--marca-icone-gradiente-fim: ${tema.marcaIconeGradienteFim}`,
    `--sidebar-item-texto-perigo: ${tema.sidebarItemTextoPerigo}`,
    `--sidebar-item-overlay-perigo: ${tema.sidebarItemOverlayPerigo}`,
    `--borda: ${tema.borda}`,
    `--borda-forte: ${tema.bordaForte}`,
    `--borda-suave: ${tema.bordaSuave}`,
    `--cor-sucesso: ${tema.corSucesso}`,
    `--cor-atencao: ${tema.corAtencao}`,
    `--cor-atencao-escura: ${tema.corAtencaoEscura}`,
    `--cor-ia: ${tema.corIa}`,
    `--cor-info: ${tema.corInfo}`,
    `--cor-destaque-2: ${tema.corDestaque2}`,
    `--cor-destaque-3: ${tema.corDestaque3}`,
    `--cor-erro: ${tema.corErro}`,
    `--cor-erro-suave: ${tema.corErroSuave}`,
    `--fonte-base: ${tema.fonteBase}`,
    `--fonte-mono: ${tema.fonteMono}`,
    `--texto-xs: ${tema.textoXs}`,
    `--texto-sm: ${tema.textoSm}`,
    `--texto-base: ${tema.textoBase}`,
    `--texto-md: ${tema.textoMd}`,
    `--texto-lg: ${tema.textoLg}`,
    `--texto-xl: ${tema.textoXl}`,
    `--texto-2xl: ${tema.texto2xl}`,
    `--raio-sm: ${tema.raioSm}`,
    `--raio-md: ${tema.raioMd}`,
    `--raio-lg: ${tema.raioLg}`,
    `--raio-xl: ${tema.raioXl}`,
    `--raio-pill: ${tema.raioPill}`,
    `--sombra-sm: ${tema.sombraSm}`,
    `--sombra-md: ${tema.sombraMd}`,
    `--sombra-lg: ${tema.sombraLg}`,
    `--sombra-xl: ${tema.sombraXl}`,
    `--sombra-primaria: ${tema.sombraPrimaria}`,

    // Aliases shadcn/ui — sobrescrevem os valores default de globals.css sem tocar em nenhum
    // componente (shadcn já lê essas variáveis, nunca cor literal).
    `--background: ${tema.fundoApp}`,
    `--foreground: ${tema.textoPadrao}`,
    `--card: ${tema.fundoSuperficie}`,
    `--card-foreground: ${tema.textoPadrao}`,
    `--popover: ${tema.fundoSuperficie}`,
    `--popover-foreground: ${tema.textoPadrao}`,
    `--primary: ${tema.corPrimaria}`,
    `--primary-foreground: ${tema.corPrimariaTexto}`,
    `--secondary: ${tema.fundoSutil}`,
    `--secondary-foreground: ${tema.textoPadrao}`,
    `--muted: ${tema.fundoSutil}`,
    `--muted-foreground: ${tema.textoSuave}`,
    `--accent: ${tema.corPrimariaSuave}`,
    `--accent-foreground: ${tema.corPrimaria}`,
    `--destructive: ${tema.corErro}`,
    `--border: ${tema.borda}`,
    `--input: ${tema.bordaForte}`,
    `--ring: ${tema.corPrimaria}`,
    `--radius: ${tema.raioMd}`,

    // shadcn já nomeia um conjunto de variáveis --sidebar-* (base-nova) — reaproveitamos em vez
    // de inventar nomes novos, para qualquer componente shadcn de sidebar futuro já vir themeado.
    `--sidebar: ${tema.fundoSidebar}`,
    `--sidebar-foreground: ${tema.textoSidebarItem}`,
    `--sidebar-primary: ${tema.corPrimaria}`,
    `--sidebar-primary-foreground: ${tema.corPrimariaTexto}`,
    `--sidebar-accent: ${tema.fundoSidebarBloco}`,
    `--sidebar-accent-foreground: ${tema.textoSidebarItem}`,
    `--sidebar-border: ${tema.fundoSidebarBloco}`,
    `--sidebar-ring: ${tema.corPrimaria}`,
  ];
  return `:root{${declaracoes.join(";")}}`;
}
