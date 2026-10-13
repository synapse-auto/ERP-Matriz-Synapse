// @vitest-environment node
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { createServer, type IncomingMessage, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

import { afterEach, beforeEach, describe, expect, it } from "vitest";

const RAIZ = resolve(process.cwd(), "..");
const SCRIPT = join(RAIZ, "docker/provisionamento/aplicar-marca-da-instancia.mjs");
const TEMA_B = join(RAIZ, "docker/provisionamento/instancias/disbrel/tema-b-sidebar-branca.json");
const TEMA_PADRAO = join(RAIZ, "backend/crm-app/src/main/resources/tema.json");
const LOGO_DO_CLASSPATH = join(RAIZ, "backend/crm-app/src/main/resources/logo.png");
const TOKEN = "token-secreto-do-teste-0123456789";

const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.from("conteudo-da-logo-nova")]);

interface Pedido {
  metodo: string;
  caminho: string;
  autorizacao: string | undefined;
  corpo: Buffer;
  tipo: string | undefined;
}

/** Instância de mentira: guarda tema, identidade e logo na memória, como a linha marca_da_instancia. */
class InstanciaFalsa {
  tema: Record<string, unknown> = JSON.parse(readFileSync(TEMA_PADRAO, "utf8")) as Record<string, unknown>;
  marca = "Estrutural Vidros";
  subtitulo = "CRM · Atendimento";
  logo: Buffer | null = readFileSync(LOGO_DO_CLASSPATH);
  pedidos: Pedido[] = [];
  falharEm: string | null = null; // "PUT /api/v1/config/marca/logo" → responde 500
  descartarChaveDoTema: string | null = null; // simula um servidor que perde uma chave ao gravar
  servidor!: Server;
  url = "";

  async iniciar() {
    this.servidor = createServer((req, res) => void this.atender(req, res));
    await new Promise<void>((ok) => this.servidor.listen(0, "127.0.0.1", ok));
    this.url = `http://127.0.0.1:${(this.servidor.address() as AddressInfo).port}`;
  }

  async encerrar() {
    await new Promise((ok) => this.servidor.close(ok));
  }

  private async atender(req: IncomingMessage, res: import("node:http").ServerResponse) {
    const pedacos: Buffer[] = [];
    for await (const pedaco of req) pedacos.push(pedaco as Buffer);
    const corpo = Buffer.concat(pedacos);
    const caminho = req.url ?? "";
    this.pedidos.push({
      metodo: req.method ?? "",
      caminho,
      autorizacao: req.headers.authorization,
      corpo,
      tipo: req.headers["content-type"],
    });
    const chave = `${req.method} ${caminho}`;
    const json = (status: number, valor: unknown) => {
      res.writeHead(status, { "Content-Type": "application/json" });
      res.end(JSON.stringify(valor));
    };
    if (this.falharEm === chave) return json(500, { status: 500, detail: "falha simulada" });
    if (chave === "GET /api/v1/config/tema") return json(200, this.tema);
    if (chave === "GET /api/v1/config/textos") return json(200, { app: { nome: "Synapse CRM", marca: this.marca, subtitulo: this.subtitulo } });
    if (chave === "GET /api/v1/config/logo") {
      if (!this.logo) return json(404, {});
      res.writeHead(200, { "Content-Type": "image/png" });
      return res.end(this.logo);
    }
    const autorizado = req.headers.authorization === `Bearer ${TOKEN}`;
    if (req.method === "PUT" && !autorizado) return json(403, { status: 403 });
    if (chave === "PUT /api/v1/config/marca/tema") {
      const tema = JSON.parse(corpo.toString("utf8")) as Record<string, unknown>;
      if (this.descartarChaveDoTema) delete tema[this.descartarChaveDoTema];
      this.tema = tema;
      return json(200, this.tema);
    }
    if (chave === "PUT /api/v1/config/marca/identidade") {
      const dados = JSON.parse(corpo.toString("utf8")) as { marca: string; subtitulo: string };
      this.marca = dados.marca;
      this.subtitulo = dados.subtitulo;
      return json(200, {});
    }
    if (chave === "PUT /api/v1/config/marca/logo") {
      const texto = corpo.toString("latin1");
      const inicio = texto.indexOf("\r\n\r\n") + 4;
      const fim = texto.lastIndexOf("\r\n--");
      this.logo = Buffer.from(texto.slice(inicio, fim), "latin1");
      res.writeHead(204);
      return res.end();
    }
    return json(404, {});
  }

  escritas() {
    return this.pedidos.filter((p) => p.metodo === "PUT").map((p) => `${p.metodo} ${p.caminho}`);
  }
}

interface Resultado {
  codigo: number;
  saida: string;
}

function rodar(args: string[], ambiente: Record<string, string | undefined> = {}): Promise<Resultado> {
  return new Promise((ok) => {
    const filho = spawn(process.execPath, [SCRIPT, ...args], {
      // Ambiente mínimo e explícito: o filho nunca herda SYNAPSE_TOKEN do processo de teste.
      env: { PATH: process.env.PATH ?? "", ...ambiente } as unknown as NodeJS.ProcessEnv,
      cwd: RAIZ,
    });
    let saida = "";
    filho.stdout.on("data", (d: Buffer) => (saida += d.toString()));
    filho.stderr.on("data", (d: Buffer) => (saida += d.toString()));
    filho.on("close", (codigo) => ok({ codigo: codigo ?? -1, saida }));
  });
}

let instancia: InstanciaFalsa;
let pasta: string;

beforeEach(async () => {
  instancia = new InstanciaFalsa();
  await instancia.iniciar();
  pasta = mkdtempSync(join(tmpdir(), "marca-"));
});

afterEach(async () => {
  await instancia.encerrar();
  rmSync(pasta, { recursive: true, force: true });
});

const sha = (b: Buffer | string) => createHash("sha256").update(b).digest("hex");

describe("aplicar-marca-da-instancia.mjs", () => {
  it("--dry-run valida e mostra o contraste, sem token e sem nenhuma chamada de rede", async () => {
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--dry-run"]);
    expect(r.codigo).toBe(0);
    expect(r.saida).toMatch(/válidos: (\d+)\/\1 pares/);
    expect(r.saida).toContain("| S3 | Sidebar | Item ativo (texto)");
    expect(r.saida).toContain("nada foi enviado");
    expect(instancia.pedidos).toHaveLength(0);
  });

  it("recusa --token (o segredo só vem do ambiente) e não faz chamada", async () => {
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--token", TOKEN]);
    expect(r.codigo).toBe(2);
    expect(r.saida).toContain("SYNAPSE_TOKEN");
    expect(r.saida).not.toContain(TOKEN);
    expect(instancia.pedidos).toHaveLength(0);
  });

  it("sem SYNAPSE_TOKEN fora do dry-run, para antes de qualquer chamada", async () => {
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B]);
    expect(r.codigo).toBe(2);
    expect(instancia.pedidos).toHaveLength(0);
  });

  it("recusa http fora de localhost, para o token não trafegar em claro", async () => {
    const r = await rodar(["--url", "http://crm.disbrel.example", "--tema", TEMA_B], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(2);
    expect(r.saida).toContain("https");
  });

  it("reprova tema incompleto na validação local e nada é enviado", async () => {
    const incompleto = join(pasta, "incompleto.json");
    writeFileSync(incompleto, JSON.stringify({ corPrimaria: "#6B0504" }));
    const r = await rodar(["--url", instancia.url, "--tema", incompleto], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(2);
    expect(r.saida).toContain("REPROVADO");
    expect(r.saida).toContain("chave ausente: corPrimariaHover");
    expect(instancia.pedidos).toHaveLength(0);
  });

  it("aplica tema e identidade, salva o backup antes de escrever e não vaza o token", async () => {
    const backup = join(pasta, "backup");
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", backup], {
      SYNAPSE_TOKEN: TOKEN,
      MARCA_NOME: "Disbrel",
      MARCA_SUBTITULO: "CRM · Atendimento",
    });
    expect(r.codigo).toBe(0);
    expect(r.saida).toContain("Tema aplicado e conferido por leitura.");
    expect(r.saida).toContain("--reverter");
    expect(r.saida).not.toContain(TOKEN);

    expect(instancia.escritas()).toEqual(["PUT /api/v1/config/marca/tema", "PUT /api/v1/config/marca/identidade"]);
    // O backup foi gravado antes do primeiro PUT: todas as leituras do estado atual vêm antes de qualquer escrita.
    const indiceDoPrimeiroPut = instancia.pedidos.findIndex((p) => p.metodo === "PUT");
    expect(instancia.pedidos.slice(0, indiceDoPrimeiroPut).map((p) => p.caminho)).toEqual([
      "/api/v1/config/tema",
      "/api/v1/config/textos",
      "/api/v1/config/logo",
    ]);
    expect(instancia.tema).toEqual(JSON.parse(readFileSync(TEMA_B, "utf8")));
    expect(instancia.marca).toBe("Disbrel");

    for (const arquivo of ["tema.json", "identidade.json", "manifesto.json", "logo.png"]) {
      expect(existsSync(join(backup, arquivo)), arquivo).toBe(true);
      expect(statSync(join(backup, arquivo)).mode & 0o077, `${arquivo}: só o dono lê`).toBe(0);
      expect(readFileSync(join(backup, arquivo)).includes(TOKEN), `${arquivo}: sem token`).toBe(false);
    }
    expect(JSON.parse(readFileSync(join(backup, "tema.json"), "utf8"))).toEqual(JSON.parse(readFileSync(TEMA_PADRAO, "utf8")));
    expect(JSON.parse(readFileSync(join(backup, "manifesto.json"), "utf8")).logoAnterior).toBe("classpath");

    // Todos os PUTs levaram o token; nenhum GET público precisou dele.
    for (const p of instancia.pedidos.filter((q) => q.metodo === "PUT")) expect(p.autorizacao).toBe(`Bearer ${TOKEN}`);
  });

  it("usa os valores sugeridos de nome e subtítulo quando as variáveis não existem, e avisa", async () => {
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", join(pasta, "b")], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(0);
    expect(r.saida).toContain("valores sugeridos");
    expect(instancia.marca).toBe("Disbrel");
    expect(instancia.subtitulo).toBe("CRM · Atendimento");
  });

  it("logo inexistente: não aplica logo nenhuma, avisa, e o restante é aplicado", async () => {
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--logo", join(pasta, "nao-existe.png"), "--backup", join(pasta, "b")], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(0);
    expect(r.saida).toContain("A logo NÃO será aplicada");
    expect(instancia.escritas()).not.toContain("PUT /api/v1/config/marca/logo");
  });

  it("logo que não é PNG é recusada antes de qualquer chamada", async () => {
    const falsa = join(pasta, "logo.png");
    writeFileSync(falsa, "isto nao e uma imagem");
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--logo", falsa], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(2);
    expect(r.saida).toContain("não é um PNG");
    expect(instancia.pedidos).toHaveLength(0);
  });

  it("aplica a logo (multipart, campo arquivo) e confere por leitura", async () => {
    const arquivo = join(pasta, "logo-disbrel.png");
    writeFileSync(arquivo, PNG);
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--logo", arquivo, "--backup", join(pasta, "b")], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(0);
    expect(r.saida).toContain("Logo aplicada e conferida por leitura.");
    const put = instancia.pedidos.find((p) => p.caminho === "/api/v1/config/marca/logo");
    expect(put?.tipo).toMatch(/^multipart\/form-data/);
    expect(put?.corpo.toString("latin1")).toContain('name="arquivo"');
    expect(sha(instancia.logo!)).toBe(sha(PNG));
    expect(JSON.parse(readFileSync(join(pasta, "b", "manifesto.json"), "utf8")).logoAplicada).toBe(true);
  });

  it("servidor que perde uma chave do tema: a conferência por leitura reprova e a reversão é indicada", async () => {
    instancia.descartarChaveDoTema = "corPrimaria";
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", join(pasta, "b")], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(3);
    expect(r.saida).toContain("não é igual ao enviado");
    expect(r.saida).toContain("--reverter");
  });

  it("falha num PUT depois do backup: devolve o comando exato de reversão", async () => {
    instancia.falharEm = "PUT /api/v1/config/marca/identidade";
    const backup = join(pasta, "b");
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", backup], { SYNAPSE_TOKEN: TOKEN });
    expect(r.codigo).toBe(3);
    expect(r.saida).toContain(`--reverter ${backup}`);
    expect(r.saida).toContain("HTTP 500");
    expect(r.saida).not.toContain(TOKEN);
  });

  it("token expirado/sem papel: explica a causa provável (o access token dura 15 minutos)", async () => {
    const r = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", join(pasta, "b")], { SYNAPSE_TOKEN: "outro-token" });
    expect(r.codigo).toBe(3);
    expect(r.saida).toContain("HTTP 403");
    expect(r.saida).toContain("15 minutos");
  });

  it("não sobrescreve um backup existente", async () => {
    const backup = join(pasta, "b");
    const primeira = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", backup], { SYNAPSE_TOKEN: TOKEN });
    expect(primeira.codigo).toBe(0);
    const segunda = await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", backup], { SYNAPSE_TOKEN: TOKEN });
    expect(segunda.codigo).toBe(2);
    expect(segunda.saida).toContain("Já existe um backup");
  });

  describe("reversão", () => {
    it("restaura tema e identidade do backup; logo era a do classpath, então não refaz o upload e mostra o SQL", async () => {
      const backup = join(pasta, "b");
      const estadoAntes = { tema: structuredClone(instancia.tema), marca: instancia.marca, subtitulo: instancia.subtitulo };
      const logo = join(pasta, "logo-disbrel.png");
      writeFileSync(logo, PNG);
      await rodar(["--url", instancia.url, "--tema", TEMA_B, "--logo", logo, "--backup", backup], { SYNAPSE_TOKEN: TOKEN, MARCA_NOME: "Disbrel" });
      expect(instancia.marca).toBe("Disbrel");
      instancia.pedidos = [];

      const r = await rodar(["--url", instancia.url, "--reverter", backup], { SYNAPSE_TOKEN: TOKEN });
      expect(r.codigo).toBe(0);
      expect(instancia.tema).toEqual(estadoAntes.tema);
      expect(instancia.marca).toBe(estadoAntes.marca);
      expect(instancia.subtitulo).toBe(estadoAntes.subtitulo);
      expect(instancia.escritas()).toEqual(["PUT /api/v1/config/marca/tema", "PUT /api/v1/config/marca/identidade"]);
      expect(r.saida).toContain("UPDATE marca_da_instancia SET logo_referencia_storage = NULL WHERE id = 1;");
    });

    it("quando a logo anterior era própria, refaz o upload dela", async () => {
      instancia.logo = PNG; // diferente da logo do classpath: "própria"
      const backup = join(pasta, "b");
      const nova = join(pasta, "nova.png");
      writeFileSync(nova, Buffer.concat([PNG, Buffer.from("-segunda")]));
      await rodar(["--url", instancia.url, "--tema", TEMA_B, "--logo", nova, "--backup", backup], { SYNAPSE_TOKEN: TOKEN });
      expect(instancia.logo!.toString("latin1")).toContain("-segunda");
      const r = await rodar(["--url", instancia.url, "--reverter", backup], { SYNAPSE_TOKEN: TOKEN });
      expect(r.codigo).toBe(0);
      expect(r.saida).toContain("Logo restaurada a partir do backup.");
      expect(sha(instancia.logo!)).toBe(sha(PNG));
    });

    it("recusa reverter um backup de outra instância", async () => {
      const backup = join(pasta, "b");
      await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", backup], { SYNAPSE_TOKEN: TOKEN });
      const outra = new InstanciaFalsa();
      await outra.iniciar();
      try {
        const r = await rodar(["--url", outra.url, "--reverter", backup], { SYNAPSE_TOKEN: TOKEN });
        expect(r.codigo).toBe(2);
        expect(r.saida).toContain("O backup é da instância");
        expect(outra.escritas()).toEqual([]);
      } finally {
        await outra.encerrar();
      }
    });

    it("--dry-run da reversão só descreve o que faria", async () => {
      const backup = join(pasta, "b");
      await rodar(["--url", instancia.url, "--tema", TEMA_B, "--backup", backup], { SYNAPSE_TOKEN: TOKEN });
      instancia.pedidos = [];
      const r = await rodar(["--url", instancia.url, "--reverter", backup, "--dry-run"]);
      expect(r.codigo).toBe(0);
      expect(r.saida).toContain("nada foi enviado");
      expect(instancia.pedidos).toHaveLength(0);
    });
  });
});
