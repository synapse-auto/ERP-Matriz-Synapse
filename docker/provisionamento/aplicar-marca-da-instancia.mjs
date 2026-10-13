#!/usr/bin/env node
// Aplica (ou reverte) a marca de uma instância pela API do CRM: tema, identidade (nome e subtítulo) e logo.
//
// Usa só o que já existe (E31b): PUT /api/v1/config/marca/{tema,identidade,logo}. A leitura vale na
// requisição seguinte, sem reiniciar nada. Node >= 18, sem dependências.
//
// Segurança:
//   - o token vem SÓ do ambiente (SYNAPSE_TOKEN); não existe argumento --token, para não parar no histórico
//     do shell nem na lista de processos. O token nunca é impresso, gravado em arquivo ou enviado a outro host;
//   - antes de qualquer PUT o tema é validado aqui (mesmas chaves do tema padrão, cores legíveis, contraste),
//     porque o servidor aceita qualquer JSON e um tema incompleto derruba o visual da instância inteira;
//   - o estado atual (tema, identidade, logo) é salvo em disco antes de alterar, para a reversão.
//
// Veja docker/provisionamento/instancias/disbrel/README.md para o passo a passo.

import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { calcularContraste, tabelaDeContraste, validarTema } from "./marca/contraste-tema.mjs";

const AQUI = dirname(fileURLToPath(import.meta.url));
const TEMA_PADRAO = resolve(AQUI, "../../backend/crm-app/src/main/resources/tema.json");
const LOGO_PADRAO = resolve(AQUI, "../../backend/crm-app/src/main/resources/logo.png");
const SUGESTAO_NOME = "Disbrel";
const SUGESTAO_SUBTITULO = "CRM · Atendimento";
const ASSINATURA_PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const TEMPO_LIMITE_MS = 20_000;

const AJUDA = `Aplica ou reverte a marca da instância (tema, nome/subtítulo e logo) pela API do CRM.

Aplicar:
  node docker/provisionamento/aplicar-marca-da-instancia.mjs --url <URL> --tema <tema.json> [--logo <logo.png>] [--dry-run]

Reverter (usa o backup gravado na aplicação):
  node docker/provisionamento/aplicar-marca-da-instancia.mjs --url <URL> --reverter <diretorio-do-backup>

Opções:
  --url <URL>            endereço do CRM (https; http só para localhost)
  --tema <arquivo>       tema a aplicar
  --logo <arquivo.png>   logo PNG; se o arquivo não existir, a logo NÃO é aplicada
  --backup <diretorio>   onde gravar o backup (padrão: ./backup-marca-<data UTC>)
  --tema-padrao <arq>    tema.json de referência para validar as chaves (padrão: o do repositório)
  --dry-run              só valida e mostra o que enviaria; não precisa de token nem de rede
  --reverter <dir>       restaura tema, identidade e logo do backup
  --ajuda                esta mensagem

Ambiente:
  SYNAPSE_TOKEN     obrigatório fora do --dry-run: access token de GESTOR, SUBGESTOR ou ADMINISTRADOR
  MARCA_NOME        nome exibido pós-login (sugestão: ${SUGESTAO_NOME})
  MARCA_SUBTITULO   subtítulo pós-login (sugestão: ${SUGESTAO_SUBTITULO})
`;

class ErroDeUso extends Error {}
class ErroDeAplicacao extends Error {}

function lerArgumentos(argv) {
  const opcoes = { dryRun: false };
  const comValor = {
    "--url": "url",
    "--tema": "tema",
    "--logo": "logo",
    "--backup": "backup",
    "--tema-padrao": "temaPadrao",
    "--reverter": "reverter",
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--ajuda" || arg === "-h" || arg === "--help") {
      opcoes.ajuda = true;
    } else if (arg === "--dry-run") {
      opcoes.dryRun = true;
    } else if (arg === "--token" || arg.startsWith("--token=")) {
      throw new ErroDeUso("Não existe --token: use a variável de ambiente SYNAPSE_TOKEN (argumento vai para o histórico do shell).");
    } else if (comValor[arg]) {
      const valor = argv[++i];
      if (valor === undefined || valor.startsWith("--")) throw new ErroDeUso(`${arg} precisa de um valor.`);
      opcoes[comValor[arg]] = valor;
    } else {
      throw new ErroDeUso(`Argumento desconhecido: ${arg}`);
    }
  }
  return opcoes;
}

function urlBase(texto) {
  let url;
  try {
    url = new URL(texto);
  } catch {
    throw new ErroDeUso(`--url inválida: ${texto}`);
  }
  const local = ["localhost", "127.0.0.1", "::1", "[::1]"].includes(url.hostname);
  if (url.protocol !== "https:" && !(url.protocol === "http:" && local)) {
    throw new ErroDeUso("--url deve ser https (http só é aceito para localhost); o token não pode trafegar em claro.");
  }
  return url.origin;
}

function lerJson(caminho, descricao) {
  let texto;
  try {
    texto = readFileSync(caminho, "utf8");
  } catch {
    throw new ErroDeUso(`${descricao} não encontrado: ${caminho}`);
  }
  try {
    return JSON.parse(texto);
  } catch (erro) {
    throw new ErroDeUso(`${descricao} não é um JSON válido (${caminho}): ${erro.message}`);
  }
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function nomeSeguroDeArquivo(caminho) {
  const base = basename(caminho).replace(/[^A-Za-z0-9._-]/g, "_");
  return base === "" ? "logo.png" : base;
}

/** Igualdade estrutural de JSON, insensível à ordem das chaves (o Postgres JSONB reordena). */
function jsonIgual(a, b) {
  if (a === b) return true;
  if (typeof a !== "object" || typeof b !== "object" || a === null || b === null) return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  const chavesA = Object.keys(a);
  const chavesB = Object.keys(b);
  if (chavesA.length !== chavesB.length) return false;
  return chavesA.every((chave) => chave in b && jsonIgual(a[chave], b[chave]));
}

/**
 * Uma instância sem logo própria serve a logo do classpath (GET /logo devolve 200 em vez de 404). Restaurá-la
 * pela API gravaria uma cópia no storage e deixaria a linha da marca diferente de NULL; por isso o backup
 * registra de onde a logo veio e a reversão só refaz o upload quando ela era própria.
 */
function origemDaLogoAtual(bytes) {
  if (!bytes) return "ausente";
  if (!existsSync(LOGO_PADRAO)) return "desconhecida";
  return sha256(readFileSync(LOGO_PADRAO)) === sha256(bytes) ? "classpath" : "propria";
}

const SQL_ROLLBACK_COMPLETO = `UPDATE marca_da_instancia
   SET tema = NULL, logo_referencia_storage = NULL, nome_da_marca = NULL, subtitulo = NULL,
       atualizado_por_id = NULL, atualizado_em = NULL
 WHERE id = 1;`;
const SQL_ROLLBACK_DA_LOGO = "UPDATE marca_da_instancia SET logo_referencia_storage = NULL WHERE id = 1;";

function criarCliente(base, token) {
  async function chamar(metodo, caminho, { json, formulario } = {}) {
    const cabecalhos = {};
    if (token) cabecalhos.Authorization = `Bearer ${token}`;
    let corpo;
    if (json !== undefined) {
      cabecalhos["Content-Type"] = "application/json";
      corpo = JSON.stringify(json);
    } else if (formulario !== undefined) {
      corpo = formulario;
    }
    let resposta;
    try {
      resposta = await fetch(`${base}${caminho}`, {
        method: metodo,
        headers: cabecalhos,
        body: corpo,
        redirect: "error",
        signal: AbortSignal.timeout(TEMPO_LIMITE_MS),
      });
    } catch (erro) {
      throw new ErroDeAplicacao(`${metodo} ${caminho}: sem resposta (${erro.cause?.code ?? erro.name}).`);
    }
    return resposta;
  }

  async function exigir(metodo, caminho, opcoes, esperados) {
    const resposta = await chamar(metodo, caminho, opcoes);
    if (!esperados.includes(resposta.status)) {
      const corpo = (await resposta.text()).slice(0, 300).replace(/\s+/g, " ");
      const dica =
        resposta.status === 401 || resposta.status === 403
          ? " (token expirado ou sem papel de gestão; o access token dura 15 minutos)"
          : "";
      throw new ErroDeAplicacao(`${metodo} ${caminho}: HTTP ${resposta.status}${dica}. ${corpo}`);
    }
    return resposta;
  }

  return {
    lerTema: async () => (await exigir("GET", "/api/v1/config/tema", {}, [200])).json(),
    lerTextos: async () => (await exigir("GET", "/api/v1/config/textos", {}, [200])).json(),
    /** @returns {Promise<Buffer|null>} null quando a instância não tem logo própria (404). */
    lerLogo: async () => {
      const resposta = await exigir("GET", "/api/v1/config/logo", {}, [200, 404]);
      return resposta.status === 404 ? null : Buffer.from(await resposta.arrayBuffer());
    },
    gravarTema: (tema) => exigir("PUT", "/api/v1/config/marca/tema", { json: tema }, [200]),
    gravarIdentidade: (marca, subtitulo) =>
      exigir("PUT", "/api/v1/config/marca/identidade", { json: { marca, subtitulo } }, [200]),
    gravarLogo: (bytes, nome) => {
      const formulario = new FormData();
      formulario.append("arquivo", new Blob([bytes], { type: "image/png" }), nome);
      return exigir("PUT", "/api/v1/config/marca/logo", { formulario }, [204]);
    },
  };
}

function resumirLogo(opcoes, escrever) {
  if (!opcoes.logo) return { caminho: null, bytes: null };
  const caminho = resolve(opcoes.logo);
  if (!existsSync(caminho)) {
    escrever(`AVISO: logo não encontrada em ${caminho}. A logo NÃO será aplicada (nenhuma é inventada).`);
    return { caminho: null, bytes: null };
  }
  const bytes = readFileSync(caminho);
  if (bytes.length === 0 || !bytes.subarray(0, 8).equals(ASSINATURA_PNG)) {
    throw new ErroDeUso(`${caminho} não é um PNG (assinatura de bytes inválida); a API só aceita PNG.`);
  }
  return { caminho, bytes };
}

async function aplicar(opcoes, ambiente, escrever) {
  if (!opcoes.url) throw new ErroDeUso("Informe --url.");
  if (!opcoes.tema) throw new ErroDeUso("Informe --tema.");
  const base = urlBase(opcoes.url);
  const padrao = lerJson(resolve(opcoes.temaPadrao ?? TEMA_PADRAO), "Tema padrão de referência");
  const caminhoTema = resolve(opcoes.tema);
  const tema = lerJson(caminhoTema, "Tema");

  const problemas = validarTema(tema, padrao);
  if (problemas.length > 0) {
    escrever("Tema REPROVADO na validação local; nada foi enviado:");
    for (const problema of problemas) escrever(`  - ${problema}`);
    throw new ErroDeUso("Corrija o tema e tente de novo.");
  }

  const nome = (ambiente.MARCA_NOME ?? "").trim() || SUGESTAO_NOME;
  const subtitulo = (ambiente.MARCA_SUBTITULO ?? "").trim() || SUGESTAO_SUBTITULO;
  const logo = resumirLogo(opcoes, escrever);
  const contraste = calcularContraste(tema);

  escrever(`Instância:   ${base}`);
  escrever(`Tema:        ${caminhoTema} (${Object.keys(tema).length} chaves; estrutura e contraste válidos: ${contraste.length}/${contraste.length} pares)`);
  escrever(`Identidade:  nome "${nome}", subtítulo "${subtitulo}"${ambiente.MARCA_NOME ? "" : " (valores sugeridos; defina MARCA_NOME e MARCA_SUBTITULO para mudar)"}`);
  escrever(`Logo:        ${logo.caminho ? `${logo.caminho} (${logo.bytes.length} bytes, PNG)` : "não será aplicada"}`);

  if (opcoes.dryRun) {
    escrever("\nContraste calculado (WCAG 2.x):\n");
    escrever(tabelaDeContraste(tema, padrao));
    escrever("\n--dry-run: nada foi enviado e nenhum token foi lido.");
    return 0;
  }

  const token = ambiente.SYNAPSE_TOKEN;
  if (!token) throw new ErroDeUso("Defina SYNAPSE_TOKEN (access token de um gestor) no ambiente.");
  const cliente = criarCliente(base, token);

  // 1. Backup do estado atual, antes de alterar qualquer coisa.
  const pasta = resolve(opcoes.backup ?? `backup-marca-${new Date().toISOString().replace(/[:.]/g, "-")}`);
  if (existsSync(join(pasta, "manifesto.json"))) {
    throw new ErroDeUso(`Já existe um backup em ${pasta}; use outro --backup para não sobrescrevê-lo.`);
  }
  mkdirSync(pasta, { recursive: true, mode: 0o700 });
  const temaAtual = await cliente.lerTema();
  const textosAtuais = await cliente.lerTextos();
  const logoAtual = await cliente.lerLogo();
  const identidadeAtual = { marca: textosAtuais?.app?.marca ?? null, subtitulo: textosAtuais?.app?.subtitulo ?? null };
  writeFileSync(join(pasta, "tema.json"), `${JSON.stringify(temaAtual, null, 2)}\n`, { mode: 0o600 });
  writeFileSync(join(pasta, "identidade.json"), `${JSON.stringify(identidadeAtual, null, 2)}\n`, { mode: 0o600 });
  if (logoAtual) writeFileSync(join(pasta, "logo.png"), logoAtual, { mode: 0o600 });
  else writeFileSync(join(pasta, "logo.ausente"), "A instância não tinha logo própria (GET /api/v1/config/logo devolveu 404).\n", { mode: 0o600 });
  const manifesto = {
    instancia: base,
    criadoEm: new Date().toISOString(),
    temaAplicadoSha256: sha256(JSON.stringify(tema)),
    logoAnterior: origemDaLogoAtual(logoAtual),
    logoAplicada: false,
  };
  const gravarManifesto = () =>
    writeFileSync(join(pasta, "manifesto.json"), `${JSON.stringify(manifesto, null, 2)}\n`, { mode: 0o600 });
  gravarManifesto();
  escrever(`\nBackup gravado em ${pasta}`);
  const comandoDeReversao = `node docker/provisionamento/aplicar-marca-da-instancia.mjs --url ${base} --reverter ${pasta}`;

  try {
    // 2. Tema, conferido por leitura (a escrita devolve 200 mesmo para um JSON qualquer).
    await cliente.gravarTema(tema);
    if (!jsonIgual(await cliente.lerTema(), tema)) {
      throw new ErroDeAplicacao("O tema lido de volta não é igual ao enviado.");
    }
    escrever("Tema aplicado e conferido por leitura.");

    // 3. Identidade.
    await cliente.gravarIdentidade(nome, subtitulo);
    const lida = (await cliente.lerTextos())?.app;
    if (lida?.marca !== nome || lida?.subtitulo !== subtitulo) {
      throw new ErroDeAplicacao("A identidade lida de volta não é igual à enviada.");
    }
    escrever(`Identidade aplicada: "${nome}" / "${subtitulo}".`);

    // 4. Logo, só se o arquivo existe.
    if (logo.caminho) {
      await cliente.gravarLogo(logo.bytes, nomeSeguroDeArquivo(logo.caminho));
      manifesto.logoAplicada = true;
      gravarManifesto();
      const lidaDeVolta = await cliente.lerLogo();
      if (!lidaDeVolta || sha256(lidaDeVolta) !== sha256(logo.bytes)) {
        throw new ErroDeAplicacao("A logo lida de volta não é igual à enviada.");
      }
      escrever("Logo aplicada e conferida por leitura.");
    } else {
      escrever("Logo: não aplicada.");
    }
  } catch (erro) {
    escrever(`\nFALHA após o backup: ${erro.message}`);
    escrever(`A instância pode estar com a marca parcialmente alterada. Para voltar ao estado salvo:\n  ${comandoDeReversao}`);
    throw erro;
  }

  escrever(`\nPronto. Para reverter:\n  ${comandoDeReversao}`);
  escrever("Se a instância usava os padrões do classpath (linha NULL), a reversão pela API grava uma cópia deles; para voltar a NULL de fato, use o SQL de rollback (README).");
  return 0;
}

async function reverter(opcoes, ambiente, escrever) {
  if (!opcoes.url) throw new ErroDeUso("Informe --url.");
  const base = urlBase(opcoes.url);
  const pasta = resolve(opcoes.reverter);
  const manifesto = lerJson(join(pasta, "manifesto.json"), "Manifesto do backup");
  if (manifesto.instancia !== base) {
    throw new ErroDeUso(`O backup é da instância ${manifesto.instancia}, não de ${base}. Nada foi alterado.`);
  }
  const tema = lerJson(join(pasta, "tema.json"), "Tema do backup");
  const identidade = lerJson(join(pasta, "identidade.json"), "Identidade do backup");
  const logoSalva = join(pasta, "logo.png");
  const origemDaLogo = manifesto.logoAnterior ?? "desconhecida";
  const refazerUploadDaLogo = existsSync(logoSalva) && origemDaLogo !== "classpath";

  if (opcoes.dryRun) {
    escrever(`Reverteria ${base} para o backup ${pasta}: tema (${Object.keys(tema).length} chaves), identidade "${identidade.marca}" / "${identidade.subtitulo}", ${refazerUploadDaLogo ? "logo do backup" : "logo não refeita pela API (ver SQL de rollback)"}.`);
    escrever("--dry-run: nada foi enviado.");
    return 0;
  }
  const token = ambiente.SYNAPSE_TOKEN;
  if (!token) throw new ErroDeUso("Defina SYNAPSE_TOKEN (access token de um gestor) no ambiente.");
  const cliente = criarCliente(base, token);

  await cliente.gravarTema(tema);
  if (!jsonIgual(await cliente.lerTema(), tema)) throw new ErroDeAplicacao("O tema restaurado não confere com o backup.");
  escrever("Tema restaurado e conferido.");

  if (identidade.marca && identidade.subtitulo) {
    await cliente.gravarIdentidade(identidade.marca, identidade.subtitulo);
    escrever(`Identidade restaurada: "${identidade.marca}" / "${identidade.subtitulo}".`);
  } else {
    escrever("Identidade do backup incompleta; não restaurada.");
  }

  if (refazerUploadDaLogo) {
    await cliente.gravarLogo(readFileSync(logoSalva), "logo.png");
    escrever("Logo restaurada a partir do backup.");
  } else if (manifesto.logoAplicada) {
    escrever("A logo aplicada NÃO pode ser removida pela API: a instância usava a do classpath e não existe rota para apagar a própria.");
    escrever(`Rode no banco da instância (mesmo usuário do provisionamento) para voltar à logo do classpath:\n  ${SQL_ROLLBACK_DA_LOGO}`);
    escrever(`Para voltar tema, identidade e logo todos a NULL (padrões do classpath):\n${SQL_ROLLBACK_COMPLETO}`);
  } else {
    escrever("Logo: nada a restaurar (nenhuma logo foi aplicada).");
  }
  return 0;
}

export async function executar(argv, ambiente = process.env, escrever = (linha) => console.log(linha)) {
  try {
    const opcoes = lerArgumentos(argv);
    if (opcoes.ajuda || argv.length === 0) {
      escrever(AJUDA);
      return opcoes.ajuda ? 0 : 2;
    }
    return opcoes.reverter ? await reverter(opcoes, ambiente, escrever) : await aplicar(opcoes, ambiente, escrever);
  } catch (erro) {
    if (erro instanceof ErroDeUso) {
      escrever(`Erro: ${erro.message}`);
      return 2;
    }
    if (erro instanceof ErroDeAplicacao) {
      escrever(`Erro: ${erro.message}`);
      return 3;
    }
    throw erro;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  executar(process.argv.slice(2)).then(
    (codigo) => process.exit(codigo),
    (erro) => {
      console.error(`Erro inesperado: ${erro.message}`);
      process.exit(1);
    },
  );
}
