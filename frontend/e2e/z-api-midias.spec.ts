import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { resolve } from "node:path";

import { expect, test } from "@playwright/test";

// Aplicacao real e banco descartavel local. Nao chama Z-API nem simula a API do CRM.
// O backend deve usar --synapse.agendamento.habilitado=false e saida suspensa.
// ZAPI_AUDIT_LOCAL=1 PLAYWRIGHT_BASE_URL=http://localhost:13089.
const LEAD = "e3900000-0000-4000-8000-000000000001";
const ATENDIMENTO = "e3900000-0000-4000-8000-0000000000a1";
const ROOT = resolve("..");
const CAPTURAS = resolve("output/playwright/z-api-auditoria");
const arquivos = [
  { tipo: "IMAGEM", nome: "imagem-sintetica.png", mime: "image/png", arquivo: "png", recebido: true },
  { tipo: "AUDIO", nome: "audio-sintetico.ogg", mime: "audio/ogg", arquivo: "ogg", recebido: true },
  { tipo: "VIDEO", nome: "video-sintetico.mp4", mime: "video/mp4", arquivo: "mp4", recebido: false },
  { tipo: "DOCUMENTO", nome: "documento-sintetico.pdf", mime: "application/pdf", arquivo: "pdf", recebido: true },
  { tipo: "DOCUMENTO", nome: "imagem-como-documento.png", mime: "image/png", arquivo: "png", recebido: false },
];

test.skip(process.env.ZAPI_AUDIT_LOCAL !== "1", "Exige ambiente descartavel local explicitamente preparado");
test.setTimeout(180_000);

test("midias reais: preview, reproducao, download, indisponivel e recarga em desktop/celular", async ({ page, baseURL }) => {
  expect(baseURL).toBe("http://localhost:13089");
  const temporario = mkdtempSync(resolve(tmpdir(), "synapse-zapi-ui-"));
  const bytes = {
    png: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=", "base64"),
    ogg: readFileSync(resolve(ROOT, "backend/crm-app/src/test/resources/z-api/audio-sintetico.ogg")),
    pdf: readFileSync(resolve(ROOT, "backend/crm-app/src/test/resources/z-api/documento-sintetico.pdf")),
    mp4: readFileSync(resolve(ROOT, "backend/crm-app/src/test/resources/midia/chat-interno/video-validacao.mp4")),
  };
  execFileSync("docker", ["exec", "synapse-minio", "sh", "-c",
    'mc alias set zapi-audit http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mb --ignore-existing zapi-audit/zapi-audit-local >/dev/null']);
  for (const [extensao, conteudo] of Object.entries(bytes)) {
    const caminho = resolve(temporario, `audit.${extensao}`);
    writeFileSync(caminho, conteudo);
    execFileSync("docker", ["cp", caminho, `synapse-minio:/tmp/zapi-audit.${extensao}`]);
    const mime = { png: "image/png", ogg: "audio/ogg", mp4: "video/mp4", pdf: "application/pdf" }[extensao as keyof typeof bytes];
    execFileSync("docker", ["exec", "synapse-minio", "mc", "cp", "--attr", `Content-Type=${mime}`, `/tmp/zapi-audit.${extensao}`,
      `zapi-audit/zapi-audit-local/midia/zapi-audit.${extensao}`], { stdio: "ignore" });
  }
  const mensagens = arquivos.map((item, i) => {
    const metadados = JSON.stringify({ nome: item.nome, mimetype: item.mime, tamanho: bytes[item.arquivo as keyof typeof bytes].length });
    return `INSERT INTO mensagem(id,atendimento_id,remetente_tipo,remetente_id,tipo,midia_url,midia_metadados,status_entrega,enviado_em)
      SELECT 'e3900000-0000-4000-8000-00000000000${i + 2}','${ATENDIMENTO}','${item.recebido ? "LEAD" : "ATENDENTE"}',
      ${item.recebido ? "NULL" : "id"},'${item.tipo}','midia/zapi-audit.${item.arquivo}','${metadados}'::jsonb,'ENTREGUE',now()-interval '${10 - i} minutes'
      FROM usuario WHERE email='ana@dev.local';`;
  }).join("\n");
  const sql = `DO $$ BEGIN IF current_database()<>'zapi_audit' THEN RAISE EXCEPTION 'Somente banco descartavel zapi_audit'; END IF; END $$;
    INSERT INTO lead(id,nome,telefone,status_basico,atendente_responsavel_id,ultima_interacao_em,ultima_mensagem_do_lead_em)
      SELECT '${LEAD}','Auditoria Z-API sintetica','5561999990389','EM_ATENDIMENTO',id,now(),now() FROM usuario WHERE email='ana@dev.local'
      ON CONFLICT(id) DO NOTHING;
    INSERT INTO atendimento(id,lead_id,canal_id,canal_credencial_id,atendente_id,status)
      SELECT '${ATENDIMENTO}','${LEAD}','ca000000-0000-4000-8000-000000000001','cc000000-0000-4000-8000-000000000001',id,'EM_ATENDIMENTO'
      FROM usuario WHERE email='ana@dev.local' ON CONFLICT(id) DO NOTHING;
    DELETE FROM mensagem WHERE atendimento_id='${ATENDIMENTO}';
    ${mensagens}
    INSERT INTO mensagem(id,atendimento_id,remetente_tipo,tipo,midia_metadados,status_entrega,enviado_em)
      VALUES('e3900000-0000-4000-8000-000000000009','${ATENDIMENTO}','LEAD','DOCUMENTO',
      '{"nome":"indisponivel.pdf","mimetype":"application/pdf","indisponivel":true}','ENTREGUE',now()-interval '1 minute');`;
  execFileSync("docker", ["exec", "-i", "synapse-zapi-audit-db", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "synapse", "-d", "zapi_audit"], { input: sql, stdio: ["pipe", "ignore", "inherit"] });

  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill("ana@dev.local");
  await page.locator('input[type="password"]').fill("atendente123");
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 30_000 });
  const conversa = `/atendimentos?leadId=${LEAD}&atendimentoId=${ATENDIMENTO}&visao=ATIVOS`;
  await page.goto(conversa);
  const audio = page.locator(`[data-mensagem-id="e3900000-0000-4000-8000-000000000003"]`);
  await expect(audio).toBeAttached({ timeout: 30_000 });
  await expect.poll(() => audio.locator("audio").evaluate((elemento: HTMLAudioElement) => elemento.duration)).toBeGreaterThan(0);
  const tocou = audio.locator("audio").evaluate((elemento: HTMLAudioElement) => new Promise<boolean>((concluir) => {
    elemento.addEventListener("playing", () => concluir(true), { once: true });
  }));
  await audio.getByRole("button", { name: /reproduzir/i }).click();
  expect(await tocou).toBe(true);
  await expect.poll(() => page.locator("video").evaluate((elemento: HTMLVideoElement) => elemento.duration)).toBeGreaterThan(0);
  const imagem = page.locator('[data-mensagem-id="e3900000-0000-4000-8000-000000000002"]');
  await imagem.getByRole("button", { name: /imagem-sintetica\.png/i }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  for (const [indice, item] of arquivos.entries()) {
    if (item.tipo === "IMAGEM") continue;
    const bolha = page.locator(`[data-mensagem-id="e3900000-0000-4000-8000-00000000000${indice + 2}"]`);
    await bolha.getByRole("button", { name: /aç[oõ]es.*mensagem/i }).focus();
    await page.keyboard.press("Enter");
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Baixar", exact: true }).click();
    const baixado = await download;
    expect(baixado.suggestedFilename()).toBe(item.nome);
    expect(readFileSync((await baixado.path())!)).toEqual(bytes[item.arquivo as keyof typeof bytes]);
  }

  const indisponivel = page.locator('[data-mensagem-id="e3900000-0000-4000-8000-000000000009"]');
  await indisponivel.hover();
  await indisponivel.getByRole("button", { name: /aç[oõ]es.*mensagem/i }).click();
  await expect(page.getByRole("button", { name: "Baixar", exact: true })).toHaveCount(0);
  await page.keyboard.press("Escape");
  await page.reload();
  await expect(audio).toBeAttached();
  mkdirSync(CAPTURAS, { recursive: true });
  for (const viewport of [{ width: 1920, height: 1080 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await page.evaluate(() => document.fonts.ready);
    expect(await page.locator("body").evaluate((elemento) => getComputedStyle(elemento).fontFamily)).toMatch(/Inter|sans-serif/);
    await expect(indisponivel).toBeVisible();
    await page.screenshot({ path: resolve(CAPTURAS, `midias-${viewport.width}.png`), fullPage: true });
  }
});
