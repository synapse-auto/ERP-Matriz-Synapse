// Validação visual do card "informações do chatbot" (docs/69). Não é spec de CI: precisa do backend dev
// semeado em banco descartável, do Next em localhost:3000 e do Docker. Gera PNGs em CAPTURAS.
//
//   node e2e/_capturas-informacoes-chatbot.mjs
import { execSync } from "node:child_process";
import { mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

import { chromium } from "@playwright/test";

const API = process.env.API_URL ?? "http://localhost:8080";
const WEB = process.env.WEB_URL ?? "http://localhost:3000";
const TOKEN = process.env.SYNAPSE_TOKEN_INTERNO ?? "token-visual-card";
const PG = ["exec", "-i", "synapse-postgres", "psql", "-U", "synapse", "-d", process.env.DB ?? "synapse_card_visual", "-v", "ON_ERROR_STOP=1", "-q"];
const CAPTURAS = resolve(process.env.CAPTURAS ?? "../docs/evidencias/informacoes-chatbot");
const LEAD = "e2150000-0000-4000-8000-000000000001";
const ATENDIMENTO = "e2150000-0000-4000-8000-0000000000a1";
const URL_CONVERSA = `${WEB}/atendimentos?leadId=${LEAD}&atendimentoId=${ATENDIMENTO}&visao=ATIVOS`;

mkdirSync(CAPTURAS, { recursive: true });

function sql(texto) {
  execSync(`docker ${PG.join(" ")}`, { input: texto, stdio: ["pipe", "inherit", "inherit"] });
}

async function postarCard(chave, conteudo) {
  const resposta = await fetch(`${API}/internal/v1/atendimentos/${ATENDIMENTO}/informacoes-do-chatbot`, {
    method: "POST",
    headers: { "X-Synapse-Token": TOKEN, "Idempotency-Key": chave, "Content-Type": "application/json" },
    body: JSON.stringify({ conteudo }),
  });
  return { status: resposta.status, corpo: await resposta.text() };
}

async function entrar(page, email, senha) {
  await page.goto(`${WEB}/login`);
  await page.waitForLoadState("networkidle");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator('input[type="password"]').fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await page.waitForURL(/\/atendimentos/);
}

async function foto(page, nome) {
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${CAPTURAS}/${nome}.png` });
  console.log("captura:", nome);
}

const CARD = '[data-slot="cartao-informacoes-chatbot"]';
const requisicoesDeCards = [];
const navegador = await chromium.launch();
try {
  const contexto = await navegador.newContext({ viewport: { width: 1280, height: 800 }, locale: "pt-BR", timezoneId: "America/Sao_Paulo" });
  const page = await contexto.newPage();
  page.on("request", (r) => r.url().includes("/informacoes-do-chatbot") && requisicoesDeCards.push(r.method() + " " + r.url()));

  // 1) Flag DESLIGADA: conversa normal, nenhuma requisição de cards.
  await entrar(page, "ana@dev.local", "atendente123");
  await page.goto(URL_CONVERSA);
  await page.getByText("Perfeito! Vou te passar").last().waitFor();
  await foto(page, "01-flag-desligada-conversa-normal");
  console.log("requisicoes de cards com flag desligada:", requisicoesDeCards.length);
  const recusa = await postarCard("visual-flag-off", "não deve gravar");
  console.log("POST com flag desligada ->", recusa.status, recusa.corpo.slice(0, 160));

  // 2) Liga a flag com o SCRIPT OPERACIONAL real (o mesmo que a Femina vai rodar) e recarrega.
  sql(readFileSync(resolve("../docker/provisionamento/habilitar-informacoes-do-chatbot.sql"), "utf8"));
  await page.goto(URL_CONVERSA);
  await page.getByText("Perfeito! Vou te passar").last().waitFor();

  // 3) Tempo real: com a conversa ABERTA, o n8n entrega as informações; o card aparece sem recarregar.
  const conteudo =
    "Cliente solicitou limpeza de pele e prefere atendimento pela manhã. Atendimento iniciado anteriormente para pré-agendamento, mas agora pediu transferência direta. Dados já informados em contexto: nome Marina Souza, telefone 5561999990111, convênio Unimed, primeira consulta, preferência por horário da manhã.";
  const antes = requisicoesDeCards.length;
  const ok = await postarCard("visual-ocorrencia-1:informacoes", conteudo);
  console.log("POST informacoes ->", ok.status, ok.corpo.slice(0, 160));
  await page.locator(CARD).first().waitFor({ timeout: 15000 });
  await foto(page, "02-card-chegou-em-tempo-real-sem-recarregar");
  console.log("requisicoes de cards desde a flag ligada:", requisicoesDeCards.length - antes);

  // 4) Retry da mesma ocorrência: nada duplica.
  const retry = await postarCard("visual-ocorrencia-1:informacoes", conteudo);
  console.log("retry ->", retry.status, "(mesmo corpo:", retry.corpo === ok.corpo, ")");
  console.log("cards na tela:", await page.locator(CARD).count());

  // 5) Atendente responde DEPOIS do card; recarrega para ver a ordem cronológica completa.
  sql(`INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega, enviado_em)
       SELECT gen_random_uuid(), '${ATENDIMENTO}', 'ATENDENTE', u.id, 'TEXTO',
              'Oi, Marina! Aqui é a Ana. Vi seu pedido de limpeza de pele pela manhã — vou conferir a agenda.', 'ENTREGUE', now() + interval '2 seconds'
         FROM usuario u WHERE u.email = 'ana@dev.local';`);
  await page.waitForTimeout(2500);
  await page.goto(URL_CONVERSA);
  await page.getByText("Vi seu pedido").last().waitFor();
  await foto(page, "03-historico-card-entre-ia-e-atendente");

  // 6) Conteúdo longo: nasce recolhido e expande.
  const longo = Array.from({ length: 14 }, (_, i) => `Pergunta ${i + 1}: resposta coletada pelo chatbot com algum detalhe relevante para a equipe.`).join("\n");
  await postarCard("visual-ocorrencia-2:informacoes", longo);
  await page.locator(CARD).nth(1).waitFor({ timeout: 15000 });
  await page.getByRole("button", { name: "Ver tudo" }).scrollIntoViewIfNeeded();
  await foto(page, "04-card-longo-recolhido");
  await page.getByRole("button", { name: "Ver tudo" }).click();
  await page.getByRole("button", { name: "Ver menos" }).scrollIntoViewIfNeeded();
  await foto(page, "05-card-longo-expandido");

  // 7) HTML e texto estranho viram texto puro.
  await postarCard("visual-ocorrencia-3:informacoes", '<script>alert(1)</script> <b>negrito?</b> <img src=x onerror=alert(2)>\nlinha 2 com acentuação: ção ã é');
  await page.locator(CARD).nth(2).waitFor({ timeout: 15000 });
  await page.locator(CARD).nth(2).scrollIntoViewIfNeeded();
  await foto(page, "06-html-renderizado-como-texto");

  // 8) Mobile.
  const celular = await navegador.newContext({ viewport: { width: 390, height: 844 }, locale: "pt-BR", isMobile: true, hasTouch: true });
  const mobile = await celular.newPage();
  await entrar(mobile, "ana@dev.local", "atendente123");
  await mobile.goto(URL_CONVERSA);
  await mobile.locator(CARD).first().waitFor({ timeout: 20000 });
  await mobile.locator(CARD).first().scrollIntoViewIfNeeded();
  await foto(mobile, "07-mobile-390px");
  const estouro = await mobile.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  console.log("mobile com rolagem horizontal da página:", estouro);

  // 9) Colega sem acesso (Bruno) não vê a conversa nem o card.
  const colega = await navegador.newContext({ viewport: { width: 1280, height: 800 }, locale: "pt-BR" });
  const pb = await colega.newPage();
  await entrar(pb, "bruno@dev.local", "atendente123");
  await pb.goto(URL_CONVERSA);
  await pb.waitForTimeout(2500);
  const viuCard = await pb.locator(CARD).count();
  const viuTexto = await pb.getByText("Marina Souza").count();
  console.log("Bruno: cards visiveis =", viuCard, "| menções à cliente =", viuTexto);
  await foto(pb, "08-colega-sem-acesso");
} finally {
  await navegador.close();
}
