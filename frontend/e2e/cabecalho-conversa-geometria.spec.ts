import { expect, test, type Browser, type Locator, type Page } from "@playwright/test";

/**
 * Geometria real do cabeçalho da conversa — o que o jsdom não mede.
 *
 * Defeito reproduzido: entre 640px (layout estreito) e a soma lista + painel + conversa, a grade
 * `346px | minmax(0,1fr) | 344px` deixava a coluna da conversa com 0–34px. O nome e o número
 * sumiam (0px de largura) e a barra de ações vazava para a esquerda, para baixo da lista e do
 * painel — o "⋯" ficava fora da área clicável ("menu não funciona"). Ex.: 1024px com zoom 150%.
 *
 * Zoom do navegador = viewport CSS menor com `deviceScaleFactor` igual ao zoom, na mesma largura
 * física. Pré-requisito: o mesmo de `cabecalho-conversa.spec.ts` (seed dev + fixture SQL).
 */
const LEAD_ID = "e2100000-0000-4000-8000-000000000001";
const ATENDIMENTO_ID = "e2100000-0000-4000-8000-0000000000a1";
const TELEFONE = "5561999990000";
const CAPTURAS = process.env.PLAYWRIGHT_CAPTURAS ?? "test-results/cabecalho-conversa-geometria";
/** Abaixo disto o nome deixa de identificar o lead (≈ 6 caracteres em negrito). */
const LARGURA_MINIMA_LEGIVEL_DO_NOME = 48;

const PAPEIS = {
  atendente: { email: "ana@dev.local", senha: "atendente123", visao: "ATIVOS" },
  gestor: { email: "gestor@dev.local", senha: "gestor123", visao: "TODOS" },
} as const;

type Caso = { papel: keyof typeof PAPEIS; largura: number; zoom: number };
const CASOS: Caso[] = [
  ...[1920, 1366, 1280, 1024, 800, 390].flatMap((largura) =>
    [1, 1.25, 1.5].map((zoom) => ({ papel: "atendente" as const, largura, zoom }))),
  ...[1920, 1366, 1280, 1024, 800].map((largura) => ({ papel: "gestor" as const, largura, zoom: 1 })),
];

async function abrir(browser: Browser, { papel, largura, zoom }: Caso) {
  const contexto = await browser.newContext({
    deviceScaleFactor: zoom,
    viewport: { width: Math.round(largura / zoom), height: Math.round(800 / zoom) },
  });
  const page = await contexto.newPage();
  const erros: string[] = [];
  page.on("pageerror", (erro) => erros.push(String(erro)));
  const { email, senha, visao } = PAPEIS[papel];
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator('input[type="password"]').fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
  await page.goto(`/atendimentos?leadId=${LEAD_ID}&atendimentoId=${ATENDIMENTO_ID}&visao=${visao}`);
  await expect(page.locator('[data-slot="cabecalho-conversa"]')).toBeVisible();
  return { page, contexto, erros };
}

async function definirPainel(page: Page, aberto: boolean) {
  const alvo = aberto ? "Reabrir detalhes do lead" : "Retrair detalhes do lead";
  const direto = page.getByRole("button", { name: alvo });
  if (await direto.isVisible().catch(() => false)) {
    await direto.click();
  } else if (aberto) {
    // Pode estar no "⋯".
    const menu = page.getByTestId("menu-mais-acoes");
    if (await menu.isVisible().catch(() => false)) {
      await menu.click();
      const item = page.getByRole("menuitem", { name: alvo });
      if (await item.isVisible().catch(() => false)) await item.click();
      else await page.keyboard.press("Escape");
    }
  }
  await page.waitForTimeout(300);
}

/** Retângulos reais; `faixaDoTelefone` é o trecho do número dentro do subtítulo. */
async function geometria(page: Page) {
  return page.locator('[data-slot="cabecalho-conversa"]').evaluate((cabecalho, telefone) => {
    const caixa = (el: { getBoundingClientRect(): DOMRect } | null) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x, y: r.y, largura: r.width, altura: r.height, direita: r.right, base: r.bottom };
    };
    const nome = cabecalho.querySelector('[data-slot="nome-do-lead"]');
    const subtitulo = cabecalho.querySelector('[data-slot="subtitulo-do-lead"]');
    let faixaDoTelefone = null;
    const texto = subtitulo?.firstChild;
    const inicio = texto?.textContent?.indexOf(telefone) ?? -1;
    if (texto && inicio >= 0) {
      const faixa = document.createRange();
      faixa.setStart(texto, inicio);
      faixa.setEnd(texto, inicio + telefone.length);
      faixaDoTelefone = caixa(faixa);
    }
    const barra = cabecalho.querySelector('[data-slot="acoes-cabecalho"]')!;
    const controles = [...barra.querySelectorAll(":scope > div[data-medida] button, :scope > div[data-medida] a, :scope > button")]
      .map((controle) => ({ rotulo: controle.getAttribute("aria-label") || controle.textContent?.trim() || "", ...caixa(controle)! }));
    const coluna = cabecalho.parentElement!;
    return {
      coluna: caixa(coluna)!,
      nome: caixa(nome),
      subtitulo: caixa(subtitulo),
      faixaDoTelefone,
      controles,
      rolagemDaPagina: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      rolagemDoCabecalho: cabecalho.scrollWidth - cabecalho.clientWidth,
    };
  }, TELEFONE);
}

type Caixa = { x: number; y: number; direita: number; base: number };
function cruzam(a: Caixa, b: Caixa) {
  return a.x < b.direita - 0.5 && b.x < a.direita - 0.5 && a.y < b.base - 0.5 && b.y < a.base - 0.5;
}

async function verificarCabecalho(page: Page, id: string) {
  const g = await geometria(page);
  await page.screenshot({ path: `${CAPTURAS}/${id}.png` });

  // Nome e número legíveis: nome com largura útil, número inteiro dentro do subtítulo visível.
  expect(g.nome, "nome renderizado").not.toBeNull();
  expect(g.nome!.largura, "largura do nome").toBeGreaterThanOrEqual(LARGURA_MINIMA_LEGIVEL_DO_NOME);
  expect(g.faixaDoTelefone, "número no subtítulo").not.toBeNull();
  expect(g.faixaDoTelefone!.direita, "número inteiro visível").toBeLessThanOrEqual(g.subtitulo!.direita + 0.5);
  expect(g.faixaDoTelefone!.x, "número dentro da coluna").toBeGreaterThanOrEqual(g.coluna.x - 0.5);

  // Nenhum controle sobre a identificação; todos dentro da coluna visível da conversa.
  for (const controle of g.controles) {
    for (const texto of [g.nome!, g.subtitulo!]) {
      expect(cruzam(controle, texto), `${controle.rotulo} sobre a identificação`).toBe(false);
    }
    expect(controle.x, `${controle.rotulo} vaza à esquerda da coluna`).toBeGreaterThanOrEqual(g.coluna.x - 0.5);
    expect(controle.direita, `${controle.rotulo} vaza à direita da coluna`).toBeLessThanOrEqual(g.coluna.direita + 0.5);
  }

  // Sem rolagem horizontal na aba.
  expect(g.rolagemDaPagina, "rolagem horizontal da página").toBeLessThanOrEqual(0);
  expect(g.rolagemDoCabecalho, "rolagem horizontal do cabeçalho").toBeLessThanOrEqual(1);
}

/** O ponto central do "⋯" pertence a ele (nada por cima) e o menu abre dentro da janela. */
async function verificarMenu(page: Page, id: string) {
  const gatilho = page.getByTestId("menu-mais-acoes");
  if (!(await gatilho.isVisible().catch(() => false))) return;
  const alcancavel = await gatilho.evaluate((el) => {
    const r = el.getBoundingClientRect();
    const alvo = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
    return alvo === el || el.contains(alvo);
  });
  expect(alcancavel, "⋯ recebe o clique no seu centro").toBe(true);
  await gatilho.click();
  const menu = page.getByRole("menu");
  await expect(menu).toBeVisible();
  const caixa = (await menu.boundingBox())!;
  const janela = page.viewportSize()!;
  expect(caixa.x).toBeGreaterThanOrEqual(0);
  expect(caixa.y).toBeGreaterThanOrEqual(0);
  expect(caixa.x + caixa.width).toBeLessThanOrEqual(janela.width + 0.5);
  expect(caixa.y + caixa.height).toBeLessThanOrEqual(janela.height + 0.5);
  await page.screenshot({ path: `${CAPTURAS}/${id}-menu.png` });
  await page.keyboard.press("Escape");
  await expect(menu).toBeHidden();
}

/** Transferir está na barra ou no "⋯"; pelo menu abre o mesmo diálogo e o menu fecha. */
async function transferirPeloLugarOndeEsta(page: Page, cabecalho: Locator) {
  const naBarra = cabecalho.getByRole("button", { name: /^Transferir/ });
  if (await naBarra.isVisible().catch(() => false)) {
    await naBarra.click();
  } else {
    await page.getByTestId("menu-mais-acoes").click();
    await page.getByRole("menuitem", { name: /Transferir/ }).click();
    await expect(page.getByRole("menu")).toBeHidden();
  }
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();
}

/**
 * Só no layout estreito (`CONSULTA_TELA_ESTREITA`, < 640px CSS) o painel é, por desenho, uma tela
 * cheia sobre a conversa. Em qualquer outra largura, painel por cima do cabeçalho é o defeito.
 */
async function painelCobreACoversa(page: Page) {
  const estreita = await page.evaluate(() => window.matchMedia("(max-width: 639px)").matches);
  return estreita && await page.locator("#painel-detalhes-lead").isVisible();
}

for (const caso of CASOS) {
  const { papel, largura, zoom } = caso;
  test(`geometria ${papel} ${largura}px zoom ${zoom * 100}%: identificação legível e ⋯ utilizável`, async ({ browser }) => {
    test.setTimeout(90_000);
    const { page, contexto, erros } = await abrir(browser, caso);
    try {
      const cabecalho = page.locator('[data-slot="cabecalho-conversa"]');
      for (const painel of ["painel-aberto", "painel-fechado"] as const) {
        await definirPainel(page, painel === "painel-aberto");
        // Em tela estreita o painel cobre a conversa inteira por desenho; ali o cabeçalho é medido
        // com o painel fechado.
        if (painel === "painel-aberto" && await painelCobreACoversa(page)) continue;
        const id = `${papel}-${largura}-z${zoom * 100}-${painel}`;
        await verificarCabecalho(page, id);
        await verificarMenu(page, id);
        await transferirPeloLugarOndeEsta(page, cabecalho);
      }
      expect(erros, "erros de runtime").toEqual([]);
    } finally {
      await contexto.close();
    }
  });
}

test("redimensionar com o ⋯ aberto não deixa o menu fora da janela nem quebra o cabeçalho", async ({ browser }) => {
  const { page, contexto, erros } = await abrir(browser, { papel: "atendente", largura: 1024, zoom: 1 });
  try {
    await definirPainel(page, true);
    const gatilho = page.getByTestId("menu-mais-acoes");
    await expect(gatilho).toBeVisible();
    await gatilho.click();
    await expect(page.getByRole("menu")).toBeVisible();

    for (const largura of [900, 700, 1280]) {
      await page.setViewportSize({ width: largura, height: 800 });
      await page.waitForTimeout(300);
      const menu = page.getByRole("menu");
      if (await menu.isVisible().catch(() => false)) {
        const caixa = (await menu.boundingBox())!;
        expect(caixa.x).toBeGreaterThanOrEqual(0);
        expect(caixa.x + caixa.width).toBeLessThanOrEqual(largura + 0.5);
      }
    }
    await page.keyboard.press("Escape");
    await verificarCabecalho(page, "redimensionar-com-menu-aberto");
    expect(erros).toEqual([]);
  } finally {
    await contexto.close();
  }
});

test("teclado: o ⋯ abre com Enter, as setas percorrem os itens e Escape devolve o foco", async ({ browser }) => {
  const { page, contexto } = await abrir(browser, { papel: "atendente", largura: 1024, zoom: 1.25 });
  try {
    await definirPainel(page, false);
    const gatilho = page.getByTestId("menu-mais-acoes");
    await expect(gatilho).toBeVisible();
    await gatilho.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("menu")).toBeVisible();
    await page.keyboard.press("ArrowDown");
    const papel = await page.evaluate(() => document.activeElement?.getAttribute("role"));
    expect(["menuitem", "menuitemcheckbox"]).toContain(papel);
    await page.keyboard.press("Escape");
    await expect(page.getByRole("menu")).toBeHidden();
    await expect(gatilho).toBeFocused();
  } finally {
    await contexto.close();
  }
});
