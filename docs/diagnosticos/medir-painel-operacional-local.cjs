/* SOMENTE fixture descartável local. Extrai SQL do adaptador; não executa HTML nem acessa produção. */
const { readFileSync } = require("node:fs");
const { spawnSync } = require("node:child_process");
const { resolve } = require("node:path");
const raiz = resolve(__dirname, "../..");
const fonte = readFileSync(resolve(raiz, "backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/persistencia/painel/PainelOperacionalRepositorioJdbc.java"), "utf8");
const extrair = nome => {
  const bloco = fonte.match(new RegExp(`(?:private )?static final String ${nome} = """([\\s\\S]*?)""";`));
  if (!bloco) throw Error(`SQL ${nome} não localizado; não medir uma cópia desatualizada`);
  return bloco[1];
};
const base = extrair("BASE"), agregacao = extrair("AGREGACAO");
const filtrada = ", filtrada AS (SELECT b.id, b.lead_id, b.atendente_id, b.status, b.nome, b.empresa, b.foto_referencia, b.foto_url, b.etapa_atendimento_id, b.nao_lidas, b.atividade_em, b.espera_desde, b.pendente, b.respondido FROM base b WHERE true AND (:dono::uuid IS NULL OR b.atendente_id = :dono) AND (:etapa::uuid IS NULL OR b.etapa_atendimento_id = :etapa) AND (b.nome ILIKE :busca ESCAPE '!' OR COALESCE(b.empresa, '') ILIKE :busca ESCAPE '!')) ";
// A fila é extraída da implementação, inclusive o enriquecimento DEPOIS do LIMIT.
const inicioDetalhes = fonte.indexOf('String sql = baseFiltrada(filtro) +');
const trechoDetalhes = fonte.slice(inicioDetalhes, fonte.indexOf('return jdbc.query(sql, parametros', inicioDetalhes));
const partesDetalhes = [...trechoDetalhes.matchAll(/"""([\s\S]*?)"""|"((?:\\.|[^"\\])*)"/g)].map(m => m[1] ?? JSON.parse(`"${m[2]}"`));
if (partesDetalhes.length !== 4 || !partesDetalhes[0].includes('pagina AS MATERIALIZED')) throw Error('Contrato SQL da fila mudou; revisar medição');
const detalhes = partesDetalhes[0] + "b.status <> 'FINALIZADO'" + partesDetalhes.slice(1).join('');
const usuario = "'11000000-0000-4000-8000-000000000004'::uuid";
const substituir = (sql, amplo) => sql.replace(/(?<!:):([a-zA-Z]+)/g, (_, chave) => ({
  usuario, amplo: String(amplo), inicio: "date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo') AT TIME ZONE 'America/Sao_Paulo'",
  fim: "(date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo') + interval '1 day') AT TIME ZONE 'America/Sao_Paulo'",
  agora: "now()", corte: "now()-interval '20 minutes'", limite: "20", dono: "NULL", etapa: "NULL", busca: "'%'",
  depois: "NULL", depoisId: "NULL", tamanho: "51", limitePrevia: "120",
}[chave] ?? (() => { throw Error(`Parâmetro inesperado ${chave}`); })()));
const fixture = `
BEGIN;
DO $$ BEGIN IF current_database() <> 'painel_ui' THEN RAISE EXCEPTION 'Banco não autorizado'; END IF; END $$;
SET LOCAL statement_timeout='60s';
${process.argv.includes("--sem-jit") ? "SET LOCAL jit=off;" : ""}
INSERT INTO lead(id,nome,status_basico,atendente_responsavel_id)
SELECT ('b1000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'Benchmark sintético '||n,
  (CASE WHEN n>3000 THEN 'FINALIZADO' WHEN n%6=0 THEN 'IA' ELSE 'EM_ATENDIMENTO' END)::status_basico_lead,
  CASE WHEN n%6=0 THEN NULL WHEN n%2=0 THEN ${usuario} ELSE '11000000-0000-4000-8000-000000000005'::uuid END
FROM generate_series(1,4000) n;
INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status,iniciado_em,finalizado_em)
SELECT ('b2000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
  ('b1000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'ca000000-0000-4000-8000-000000000001',
  CASE WHEN n%6=0 THEN NULL WHEN n%2=0 THEN ${usuario} ELSE '11000000-0000-4000-8000-000000000005'::uuid END,
  (CASE WHEN n>3000 THEN 'FINALIZADO' WHEN n%6=0 THEN 'EM_IA' ELSE 'EM_ATENDIMENTO' END)::status_atendimento,
  now()-interval '8 days',CASE WHEN n>3000 THEN now()-interval '6 days' END
FROM generate_series(1,4000) n;
INSERT INTO mensagem(atendimento_id,remetente_tipo,tipo,conteudo,status_entrega,enviado_em)
SELECT ('b2000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
  (CASE WHEN m%5=0 THEN 'ATENDENTE' ELSE 'LEAD' END)::remetente_tipo,
  'TEXTO','Fixture de medição sintética','ENVIADO',
  CASE WHEN n>3000 THEN now()-interval '7 days'+m*interval '1 minute' ELSE now()-interval '2 hours'+m*interval '1 minute' END
FROM generate_series(1,4000) n CROSS JOIN generate_series(1,32) m;
ANALYZE lead; ANALYZE atendimento; ANALYZE mensagem;
SET LOCAL ROLE synapse_app;
`;
let sql = fixture;
for (const amplo of [true, false]) {
  sql += `SELECT set_config('app.papel','${amplo ? "GESTOR" : "ATENDENTE"}',true); SELECT set_config('app.usuario_id',${usuario}::text,true);\n`;
  for (let rodada = 1; rodada <= (process.argv.includes("--uma-rodada") ? 1 : 3); rodada++) {
    for (const [operacao, consulta] of [["snapshot",agregacao],["fila",detalhes]]) {
      sql += `SELECT 'PLANO_${amplo ? "gestao" : "pessoal"}_${operacao}_${rodada}';\nEXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) ${substituir(base + filtrada + consulta, amplo)};\n`;
    }
  }
}
sql += "RESET ROLE; SELECT 'FIM_PLANOS'; ROLLBACK; ANALYZE lead; ANALYZE atendimento; ANALYZE mensagem;";
const resultado = spawnSync("docker", ["exec", "-i", "synapse-painel-operacional-postgres", "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "painel", "-d", "painel_ui"], { input: sql, encoding: "utf8", maxBuffer: 32*1024*1024 });
const planos = [];
for (const match of resultado.stdout.matchAll(/PLANO_(gestao|pessoal)_(snapshot|fila)_(\d)\r?\n/g)) {
  const inicio = resultado.stdout.indexOf("[", match.index + match[0].length);
  const fim = resultado.stdout.indexOf("\n]", inicio) + 2;
  const p = JSON.parse(resultado.stdout.slice(inicio, fim))[0];
  const scans = [];
  const visitar = n => { if (n["Relation Name"] || n["Node Type"].includes("Scan")) scans.push({ no:n["Node Type"], relacao:n["Relation Name"], linhas:n["Actual Rows"], loops:n["Actual Loops"], removidas:n["Rows Removed by Filter"], indice:n["Index Name"] }); (n.Plans ?? []).forEach(visitar); };
  visitar(p.Plan);
  planos.push({ alcance:match[1], operacao:match[2], rodada:Number(match[3]), planejamentoMs:p["Planning Time"], execucaoMs:p["Execution Time"], jit:p.JIT, sharedHit:p.Plan["Shared Hit Blocks"], sharedRead:p.Plan["Shared Read Blocks"], tempRead:p.Plan["Temp Read Blocks"], tempWritten:p.Plan["Temp Written Blocks"], scans });
}
console.log(JSON.stringify({ ambiente:"PostgreSQL15 local, fixture em transação com ROLLBACK", volume:{ leads:4000, abertos:3000, historicosEncerrados:1000, mensagens:128000 }, rls:"SET LOCAL ROLE synapse_app + app.papel + app.usuario_id; não executado como dono", jitDesligado:process.argv.includes("--sem-jit"), erro:resultado.status === 0 ? null : resultado.stderr.trim(), observacao:"Primeira passagem não é cache frio de disco: inserção/ANALYZE aqueceram buffers. Sem extrapolação para produção.", planos },null,2));
if (resultado.status !== 0 || planos.length !== (process.argv.includes("--uma-rodada") ? 4 : 12)) process.exitCode=1;
