import { expect, test, type Page, type Request, type WebSocketRoute } from "@playwright/test";

/**
 * E217 — a bolha de mídia enviada não pode sumir por causa da ordem em que a confirmação HTTP e o
 * evento STOMP chegam. O transporte é controlado pelo Playwright contra o backend local real:
 *
 * - HTTP primeiro: os frames STOMP da conversa ficam retidos até a resposta do upload chegar.
 * - WebSocket primeiro: a resposta do upload fica retida até o frame MENSAGEM ser entregue.
 *
 * Pré-requisito: backend local no perfil `dev` com `WHATSAPP_URL_BASE` apontando para uma porta local
 * morta (nada sai para o WhatsApp) + `e2e/fixtures/envio-midia-ordem.sql`. Capturas em
 * `PLAYWRIGHT_CAPTURAS`, tiradas antes das asserções para servirem de "antes" contra código antigo.
 */
const LEAD_ID = "e2170000-0000-4000-8000-000000000001";
const ATENDIMENTO_ID = "e2170000-0000-4000-8000-0000000000a1";
const CAPTURAS = process.env.PLAYWRIGHT_CAPTURAS ?? "test-results/envio-midia-ordem";
const DESTINO_DA_CONVERSA = `atendimento.${ATENDIMENTO_ID}`;

type Ordem = "http-primeiro" | "websocket-primeiro";

/**
 * Proxy STOMP: repassa tudo, exceto frames MESSAGE enquanto `retendo` estiver ligado. O STOMP é um
 * socket só — quando ele atrasa, atrasam juntos o tópico da conversa e a fila de notificações; reter
 * só a conversa deixaria a notificação disparar um refetch que mascara o defeito.
 */
async function controlarStomp(page: Page) {
  const retidos: { cliente: WebSocketRoute; frame: string }[] = [];
  const entregues: string[] = [];
  let retendo = false;
  // O token vai na query string (`/ws?access_token=...`); casa só o caminho.
  await page.routeWebSocket((url) => url.pathname === "/ws", (cliente) => {
    const servidor = cliente.connectToServer();
    cliente.onMessage((frame) => servidor.send(frame));
    servidor.onMessage((dado) => {
      const frame = typeof dado === "string" ? dado : dado.toString("utf-8");
      if (retendo && frame.startsWith("MESSAGE")) {
        retidos.push({ cliente, frame });
        return;
      }
      cliente.send(frame);
      entregues.push(frame);
    });
  });
  return {
    reter() {
      retendo = true;
    },
    liberar() {
      retendo = false;
      for (const { cliente, frame } of retidos.splice(0)) {
        cliente.send(frame);
        entregues.push(frame);
      }
    },
    retidosDaConversa: () => retidos.filter(({ frame }) => frame.includes(DESTINO_DA_CONVERSA)).length,
    entregouMensagemCom: (trecho: string) =>
      entregues.some((frame) => frame.includes('"MENSAGEM"') && frame.includes(trecho)),
  };
}

async function entrarComoAna(page: Page) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill("ana@dev.local");
  await page.locator('input[type="password"]').fill("atendente123");
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/, { timeout: 30_000 });
}

/** A lista é virtualizada: espera o histórico montar e o composer liberar, não um texto específico. */
async function conversaPronta(page: Page) {
  await expect(page.locator("[data-mensagem-id]").first()).toBeAttached({ timeout: 30_000 });
  await expect(page.locator('[data-slot="composer"] input[type="file"]')).toBeEnabled({ timeout: 30_000 });
}

async function abrirConversa(page: Page) {
  await page.goto(`/atendimentos?leadId=${LEAD_ID}&atendimentoId=${ATENDIMENTO_ID}&visao=ATIVOS`);
  await conversaPronta(page);
}

function pdfSintetico(nome: string) {
  const conteudo = `%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n% ${nome}\n`;
  return { name: nome, mimeType: "application/pdf", buffer: Buffer.from(conteudo) };
}

function bolhasCom(page: Page, nome: string) {
  return page.locator("[data-mensagem-id]").filter({ hasText: nome });
}

/**
 * Linha do tempo do DOM: quantas bolhas do arquivo existem a cada mutação. Pega o sumiço
 * momentâneo ("aparece e some") que uma captura isolada não mostraria.
 */
async function observarBolhas(page: Page, nome: string) {
  await page.evaluate((trecho) => {
    const janela = window as unknown as { __contagensE217: number[] };
    janela.__contagensE217 = [];
    const contar = () =>
      Array.from(document.querySelectorAll("[data-mensagem-id]"))
        .filter((elemento) => elemento.textContent?.includes(trecho)).length;
    new MutationObserver(() => janela.__contagensE217.push(contar()))
      .observe(document.body, { subtree: true, childList: true, characterData: true });
  }, nome);
  return () => page.evaluate(() => (window as unknown as { __contagensE217: number[] }).__contagensE217);
}

function sumiuDepoisDeAparecer(contagens: number[]): boolean {
  const primeira = contagens.findIndex((quantidade) => quantidade > 0);
  return primeira >= 0 && contagens.slice(primeira).some((quantidade) => quantidade === 0);
}

async function enviarPdf(page: Page, nome: string) {
  const composer = page.locator('[data-slot="composer"]');
  await composer.locator('input[type="file"]').setInputFiles(pdfSintetico(nome));
  await composer.getByRole("button", { name: "Enviar", exact: true }).click();
}

for (const ordem of ["http-primeiro", "websocket-primeiro"] as Ordem[]) {
  test(`documento enviado com ${ordem}: exatamente uma bolha, sem reenvio, e sobrevive ao F5`, async ({ page }) => {
    test.setTimeout(120_000);
    const nome = `proposta-e217-${ordem}-${Date.now()}.pdf`;
    const envios: Request[] = [];
    page.on("request", (requisicao) => {
      if (requisicao.method() === "POST" && requisicao.url().includes("/mensagens/midia")) envios.push(requisicao);
    });

    const stomp = await controlarStomp(page);
    let liberarHttp: () => void = () => {};
    const httpLiberado = new Promise<void>((resolve) => (liberarHttp = resolve));
    await page.route("**/mensagens/midia**", async (rota) => {
      const resposta = await rota.fetch();
      if (ordem === "websocket-primeiro") await httpLiberado;
      await rota.fulfill({ response: resposta });
    });

    await entrarComoAna(page);
    await abrirConversa(page);
    if (ordem === "http-primeiro") stomp.reter();

    const linhaDoTempo = await observarBolhas(page, nome);
    const respostaDoUpload = page.waitForResponse((resposta) => resposta.url().includes("/mensagens/midia"));
    await enviarPdf(page, nome);

    if (ordem === "http-primeiro") {
      await respostaDoUpload;
      await expect.poll(() => stomp.retidosDaConversa()).toBeGreaterThan(0);
      await page.waitForTimeout(500);
      await page.screenshot({ path: `${CAPTURAS}/${ordem}-1-http-confirmou-socket-retido.png` });
      await expect(bolhasCom(page, nome)).toHaveCount(1);
      stomp.liberar();
    } else {
      await expect.poll(() => stomp.entregouMensagemCom(nome), { timeout: 15_000 }).toBe(true);
      await page.waitForTimeout(500);
      await page.screenshot({ path: `${CAPTURAS}/${ordem}-1-socket-entregou-http-retido.png` });
      await expect(bolhasCom(page, nome)).toHaveCount(1);
      liberarHttp();
      await respostaDoUpload;
    }

    await page.waitForTimeout(1_500);
    await page.screenshot({ path: `${CAPTURAS}/${ordem}-2-ambos-chegaram.png` });
    const contagens = await linhaDoTempo();
    test.info().annotations.push({ type: "linha-do-tempo", description: contagens.join(",") });
    expect(sumiuDepoisDeAparecer(contagens), `bolha sumiu em algum momento: ${contagens.join(",")}`).toBe(false);
    expect(Math.max(...contagens)).toBe(1);
    await expect(bolhasCom(page, nome)).toHaveCount(1);
    const idNaTela = await bolhasCom(page, nome).getAttribute("data-mensagem-id");
    expect(idNaTela).not.toMatch(/^temp-/);

    await page.reload();
    await conversaPronta(page);
    await expect(bolhasCom(page, nome)).toHaveCount(1);
    await page.screenshot({ path: `${CAPTURAS}/${ordem}-3-apos-recarregar.png` });
    expect(await bolhasCom(page, nome).getAttribute("data-mensagem-id")).toBe(idNaTela);
    expect(envios).toHaveLength(1);
  });
}
