import { expect, test } from "@playwright/test";

const email = process.env.PLAYWRIGHT_EMAIL ?? "admin@dev.local";
const senha = process.env.PLAYWRIGHT_SENHA ?? "admin123";

test("o Chrome entrega M4A/AAC gravavel sem conversao", async ({ page }) => {
  await page.goto("/login");

  const resultado = await page.evaluate(async () => {
    const candidatos = [
      "audio/mp4;codecs=mp4a.40.2",
      "audio/mp4",
      "audio/webm;codecs=opus",
    ];
    const suportados = candidatos.filter((tipo) =>
      MediaRecorder.isTypeSupported(tipo),
    );
    const mimeType = suportados.find((tipo) => tipo.startsWith("audio/mp4"));
    if (!mimeType)
      return { suportados, mimeType: null, blobType: null, tamanho: 0 };

    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const partes: Blob[] = [];
    const recorder = new MediaRecorder(stream, { mimeType });
    recorder.ondataavailable = (evento) => partes.push(evento.data);
    const terminou = new Promise<void>((resolve) => {
      recorder.onstop = () => resolve();
    });

    recorder.start();
    await new Promise((resolve) => window.setTimeout(resolve, 500));
    recorder.stop();
    await terminou;
    stream.getTracks().forEach((track) => track.stop());

    const blob = new Blob(partes, { type: recorder.mimeType });
    return {
      suportados,
      mimeType: recorder.mimeType,
      blobType: blob.type,
      tamanho: blob.size,
    };
  });

  expect(resultado.suportados).toContain("audio/mp4;codecs=mp4a.40.2");
  expect(resultado.mimeType).toBe("audio/mp4;codecs=mp4a.40.2");
  expect(resultado.blobType).toBe("audio/mp4;codecs=mp4a.40.2");
  expect(resultado.tamanho).toBeGreaterThan(0);
});

test("o composer grava, pre-visualiza e so entao permite enviar", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.getByLabel(/senha/i).fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/);
  await page.clock.install({ time: new Date("2026-08-03T13:00:00Z") });
  await page
    .locator('[data-slot="lista-conversas-scroll"] [data-slot="scroll-area-viewport"] button')
    .first()
    .click();

  await page.getByLabel("Gravar áudio").click();
  await expect(page.getByText("Gravando áudio", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "Parar gravação" }).click();

  await expect(page.getByLabel("Pré-visualização da gravação")).toBeVisible();
  await expect(page.getByRole("button", { name: "Enviar" })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Enviar gravação" })).toHaveCount(0);
  await page.getByRole("button", { name: "Descartar gravação" }).click();
  await expect(page.getByLabel("Pré-visualização da gravação")).toBeHidden();
});
