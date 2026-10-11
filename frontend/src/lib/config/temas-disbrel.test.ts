// @vitest-environment node
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import {
  CHAVES_OPCIONAIS_ALEM_DO_PADRAO,
  calcularContraste,
  distanciaPrimarioErro,
  validarTema,
} from "../../../../docker/provisionamento/marca/contraste-tema.mjs";
import { temaParaCssVariaveis } from "./fetch-config";
import { TemaSchema } from "./schema";

type Tema = Record<string, string>;

function sem(tema: Tema, ...chaves: string[]): Tema {
  return Object.fromEntries(Object.entries(tema).filter(([chave]) => !chaves.includes(chave)));
}

function carregar(caminhoRelativoAoRepositorio: string): Tema {
  return JSON.parse(readFileSync(resolve(process.cwd(), "..", caminhoRelativoAoRepositorio), "utf8")) as Tema;
}

const PADRAO = carregar("backend/crm-app/src/main/resources/tema.json");
const TEMAS = {
  "A (sidebar vinho)": carregar("docker/provisionamento/instancias/disbrel/tema-a-sidebar-vinho.json"),
  "B (sidebar branca)": carregar("docker/provisionamento/instancias/disbrel/tema-b-sidebar-branca.json"),
};

describe("temas candidatos da Disbrel (D1)", () => {
  describe.each(Object.entries(TEMAS))("tema %s", (_nome, tema) => {
    it("tem as mesmas chaves do tema padrão, mais só os tokens opcionais declarados", () => {
      const esperadas = [...Object.keys(PADRAO), ...CHAVES_OPCIONAIS_ALEM_DO_PADRAO].sort();
      expect(Object.keys(tema).sort()).toEqual(esperadas);
    });

    it("é aceito pelo mesmo schema que o frontend usa para o tema servido pelo backend", () => {
      const resultado = TemaSchema.safeParse(tema);
      expect(resultado.success).toBe(true);
    });

    it("passa na validação estrutural e em todos os pares de contraste", () => {
      expect(validarTema(tema, PADRAO)).toEqual([]);
    });

    it("usa #6B0504 como cor primária", () => {
      expect(tema.corPrimaria.toUpperCase()).toBe("#6B0504");
    });

    it("mantém iguais ao padrão o que a Disbrel não pediu para mudar (sucesso, atenção, tipografia, raios)", () => {
      for (const chave of [
        "corSucesso", "corAtencao", "corAtencaoEscura", "corIa", "corInfo", "corDestaque2", "corDestaque3",
        "fonteBase", "fonteMono", "textoBase", "raioMd", "sombraSm", "logoUrl", "mensagemLidaTexto",
      ]) {
        expect(tema[chave], chave).toBe(PADRAO[chave]);
      }
    });

    it("separa o erro da cor primária: erro mais claro e a distância de cor não é desprezível", () => {
      const distancia = distanciaPrimarioErro(tema);
      expect(distancia).not.toBeNull();
      expect(distancia!.deltaE).toBeGreaterThan(30);
      expect(distancia!.razaoDeLuminancia).toBeGreaterThan(2);
    });

    it("gera as variáveis CSS dos dois tokens novos", () => {
      const css = temaParaCssVariaveis(TemaSchema.parse(tema));
      expect(css).toContain(`--sidebar-texto-forte: ${tema.sidebarTextoForte}`);
      expect(css).toContain(`--mensagem-falha-texto: ${tema.mensagemFalhaTexto}`);
    });
  });

  it("os dois temas só diferem na sidebar (e nos tokens que dependem dela), nunca no conteúdo", () => {
    const [a, b] = Object.values(TEMAS);
    const diferentes = Object.keys(a).filter((chave) => a[chave] !== b[chave]);
    const permitidas = (chave: string) => /^(sidebar|textoSidebar|fundoSidebar|marcaIcone)/.test(chave);
    expect(diferentes.filter((chave) => !permitidas(chave))).toEqual([]);
  });
});

describe("tema padrão (Estrutural e Fêmina) continua exatamente como está", () => {
  it("o tema.json padrão não ganhou os tokens novos", () => {
    for (const chave of CHAVES_OPCIONAIS_ALEM_DO_PADRAO) expect(PADRAO).not.toHaveProperty(chave);
  });

  it("sem os tokens novos o frontend reproduz o comportamento antigo: texto branco e a mesma cor de perigo", () => {
    const resolvido = TemaSchema.parse(PADRAO);
    expect(resolvido.sidebarTextoForte).toBe("#FFFFFF");
    expect(resolvido.mensagemFalhaTexto).toBe(PADRAO.sidebarItemTextoPerigo);
  });

  it("se só sidebarItemTextoPerigo faltar, a falha de entrega cai na cor de erro, como antes", () => {
    const resolvido = TemaSchema.parse(sem(PADRAO, "sidebarItemTextoPerigo"));
    expect(resolvido.mensagemFalhaTexto).toBe(PADRAO.corErro);
  });
});

describe("a validação de tema reprova de verdade (a proteção que não pode ser decoração)", () => {
  const base = TEMAS["B (sidebar branca)"];

  it("reprova tema incompleto — o que derrubaria o layout raiz da instância", () => {
    const problemas = validarTema(sem(base, "corPrimaria", "fundoApp"), PADRAO);
    expect(problemas).toContain("chave ausente: corPrimaria");
    expect(problemas).toContain("chave ausente: fundoApp");
  });

  it("reprova chave desconhecida e valor que não é cor onde o padrão tem cor", () => {
    const problemas = validarTema({ ...base, corInventada: "#000000", corPrimaria: "vinho" }, PADRAO);
    expect(problemas).toContain("chave desconhecida: corInventada");
    expect(problemas.some((p) => p.startsWith("corPrimaria:"))).toBe(true);
  });

  it("reprova contraste abaixo do mínimo, informando o valor calculado", () => {
    const problemas = validarTema({ ...base, textoPadrao: "#B8A9A9" }, PADRAO);
    expect(problemas.some((p) => /^contraste C3 .*:1, mínimo 4\.5:1$/.test(p))).toBe(true);
  });

  it("reprova a regressão histórica: sidebar clara com texto forte branco", () => {
    const problemas = validarTema({ ...base, sidebarTextoForte: "#FFFFFF" }, PADRAO);
    expect(problemas.some((p) => p.startsWith("contraste S3"))).toBe(true);
    expect(problemas.some((p) => p.startsWith("contraste S8"))).toBe(true);
  });

  it("reprova falha de entrega ilegível sobre o balão de saída", () => {
    const problemas = validarTema({ ...base, mensagemFalhaTexto: "#BF360C" }, PADRAO);
    expect(problemas.some((p) => p.startsWith("contraste M1"))).toBe(true);
  });

  it("reprova um objeto que não é tema", () => {
    expect(validarTema(["x"], PADRAO)).toEqual(["o tema deve ser um objeto JSON"]);
    expect(validarTema(null, PADRAO)).toEqual(["o tema deve ser um objeto JSON"]);
  });

  it("a razão de contraste é a do WCAG: preto sobre branco dá 21:1 e #6B0504 sobre branco dá 12,74:1", () => {
    const linhas = calcularContraste({ ...base, textoForte: "#000000", fundoSuperficie: "#FFFFFF" });
    expect(linhas.find((l) => l.id === "C1")!.razao).toBeCloseTo(21, 5);
    expect(linhas.find((l) => l.id === "P3")!.razao).toBeCloseTo(12.74, 2);
  });
});
