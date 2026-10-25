# Z-API — planejamento autorizado por fases

Origem: prompt fornecido por Marcondes em 12/10/2026. Executar as cinco fases em sequência,
sem nova autorização entre fases, mas **parar para decisão** diante de contrato essencial
ausente, incompatibilidade arquitetural ou ampliação de escopo. Reler este arquivo e
[progresso.md](./progresso.md) antes de cada fase, inclusive após compactação.

## Restrições permanentes

**Complemento vigente de 25/10/2026:** autorizada protecao propria com segredo exclusivo
na URL, validado antes do corpo/fila; preparar modo de header nativo sem presumir seu nome.
O suporte afirmou existir header, mas falta nome/configuracao. Preferir nativo quando
confirmado. G1 nao bloqueia mais o codigo independente. Continuam proibidas operacoes reais.
Entregas anteriores #321–#324 ja mergeadas; complemento em PR unica sobre main, sem repetir
diffs das fases. [Contrato/roteiro vigente](./homologacao-protegida.md).

**Autorização de 25/10/2026:** corrigir o CI de todas as PRs abertas e fazer merge
sequencial após aprovação do último SHA. Esta autorização substitui somente a
proibição anterior de merge e a pendência de autorização para migrar Zod.
Não autoriza deploy, habilitar Z-API, conectar números ou abrir entrada insegura.

- Provedor planejado `z-api`, desativado em todas as instâncias.
- Não substituir Meta/Uzapi, conectar números, registrar callbacks reais, enviar mensagens,
  fazer merge/deploy, acessar VPS/Dokploy ou alterar n8n de produção.
- Usar somente fontes oficiais descobertas por `https://developer.z-api.io/llms.txt`.
  Registrar fonte de cada contrato; ausência é lacuna, não licença para inventar.
- Reutilizar CanalGateway, TradutorDeCanal, seletor, outbox e processamento durável.
  Payloads do fornecedor não entram no domínio. Não migrar históricos/IDs/telefones.
- Sem configuração/ativação, nenhuma chamada externa e nenhuma credencial obrigatória.
- Uma branch/commit/push/PR por fase. Conferir CI do último SHA antes de avançar;
  corrigir falhas da fase, nunca ignorar vermelho. Se a PR anterior estiver aberta,
  criar branch a partir dela e PR com essa branch como base; documentar ordem de merge.
- Conferir migrations em origin/main **e nas fases anteriores**, se necessárias.
- Testes específicos e negativos; backend clean verify Java 21 nas fases de código;
  frontend testes/typecheck/lint/build se alterado; relatório nos sete blocos do AGENTS.
  Separar simulador de homologação real. Atualizar progresso após cada fase.

## Fase 1 — contrato e desenho, sem adaptador

**Complemento autorizado em 24/10:** enquanto G1 impedir entrada, avançar nas partes
independentes de saída de mídia, testes, capacidades e passagem para automação. Manter PR
encadeada na última entrega real (#322), sem inventar PR de fase bloqueada. Bloqueio Zod
aceito para registro não é CI aprovado nem libera operação; distinguir avanço parcial de
fase completa no progresso/checklist.

Investigar autenticação, cinco tipos de envio, IDs, callbacks de recebimento/processamento/status,
saúde, mídia/validade, origem dos callbacks, limites/erros/idempotência/reentrega.
Entregar matriz capacidade → contrato → fonte → comprovado/lacuna, fixtures sintéticas,
configuração/referências de credenciais, correlação canal/instância/ID externo, política de
envio ambíguo, mídia segura e passagem para automação. Não presumir HMAC, autenticação de
callback por Client-Token nem entrega por HTTP 200. Gate: segurança/correlação explicitadas.

## Fase 2 — adaptador desligado e texto

Configuração opcional, seleção, credenciais só no backend/banco com referências, texto pelo
worker/outbox, IDs preservados, diagnóstico sanitizado, timeout/circuit breaker/bulkhead,
verificação operacional. Token no path exige redação de URLs em logs/exceções/tracing/métricas.
Não prometer exactly-once nem retry cego. Testar sucesso/resposta inválida/erro/timeout/ausência
de configuração e regressão Meta/Uzapi com HTTP simulado. Nenhuma ativação real.

## Fase 3 — entrada e correlação

**Complemento autorizado em 24/10:** implementar tradução/recebimento/status/correlação com
fixtures oficiais sanitizadas e testes locais, mantendo entrada de produção bloqueada. G1
passa a bloquear registro/ativação do verificador real, não a implementação da ACL testável.
Não inventar assinatura nem abrir rota pública permissiva. [Escopo local](./fase-3-entrada-local.md).

Tradutor no webhook real, texto, identificação de instância/canal, deduplicação, processamento
e status repetidos/fora de ordem/anteriores ao POST, eco fromMe sem duplicação, filtros de
grupos/Status/Story/tipos não suportados, persistência/WebSocket. Delivery não é entregue ao
destinatário. Autenticação fail-closed; sem assinatura nativa, documentar proteção e limites,
sem chamar segredo de URL de assinatura. Verificar repasse para n8n sem mudança silenciosa.
Gate: testes ponta a ponta com Postgres real, duplicação/acesso indevido violados de propósito.

## Fase 4 — mídia e capacidades

Imagem/áudio/vídeo/documento, storage privado/download autenticado. Sem mediaId copiado da
Uzapi. Link/Base64 segundo contrato e arquitetura; sem bucket público/credenciais expostas,
limites de bytes/memória, download assíncrono SSRF-safe sem tokens para host de mídia.
Testar JPG/PNG como DOCUMENTO, MIME/nome/bytes, HTTP simulado e storage real de teste.
Mapear citação/contato/localização/reação/edição/exclusão/foto; somente capacidades comprovadas,
sem controles fantasmas. Chat Interno/grupos internos fora.

## Fase 5 — fechamento sem homologação real

Regressão Meta/Uzapi, autorização/participação consentida, isolamento canal/instância,
recuperação/CPU/memória, logs sem dados sensíveis, configuração ausente e capacidades UI.
Handoff para Dylan; variáveis opcionais/defaults seguros em .env.example/README/stack quando
existirem. Runbook futuro: credenciais → conexão → callbacks → número controlado → ativação
explícita → rollback. **Não executar o runbook nesta tarefa.**
