# 74 — Mídias e Documentos na ficha do lead

## Escopo

Chat externo, ficha lateral direita. Duas seções recolhíveis independentes, com os
componentes, ícones e tokens já usados pela ficha. Não altera Chat Interno, provedores,
histórico, envio/outbox, permissões, RLS, schema ou migrations. A implementação original
partiu de `origin/main 9a2fc89`, na PR #310, e não duplicou a entrega independente da #309.
A PR #310 foi mergeada em 17/10/2026 (`c23ca850746d49c6bd887a3263e8c11b633f1897`).

## Estado verificado em 19/10/2026

Revisão da `origin/main 9204d5f`: a separação já está implementada e não precisou de outra
implementação ou mudança de contrato. Foram acrescentadas somente evidências atuais e
uma fixture SQL opt-in para categorias vazias independentemente e Mídias em duas páginas.
Não houve alteração de componentes, endpoints, consultas de produção ou Chat Interno.

Índice da revalidação: [capturas e resultados](evidencias/ficha-midias-documentos-20261019/README.md).
O helper original continua sendo o ambiente de partida. O complemento
`frontend/e2e/fixtures/ficha-midias-categorias-isoladas.sql` recusa banco diferente de
`midias_ficha`; essa recusa foi testada deliberadamente antes da aplicação no banco descartável.
Não é migration nem script de produção.

## Classificação e contratos

- **Mídias:** `mensagem.tipo` IMAGEM, VIDEO e AUDIO. O pedido atual volta a incluir áudio
  nessa seção; o corte anterior da ficha excluía AUDIO por apresentação, não por domínio.
- **Documentos:** `mensagem.tipo` DOCUMENTO. JPG/PNG com MIME de imagem continuam aqui.
- Nenhum registro é reclassificado por nome, extensão ou MIME. O visualizador conserva
  sua classificação visual existente, independente da categoria da seção.
- `GET /api/v1/leads/{leadId}/midias` mantém o array, os parâmetros pagina/tamanho/tipos,
  limite vigente e ordenação `enviado_em DESC, id DESC`. O filtro precede LIMIT/OFFSET.
- Parâmetro aditivo `incluirIndisponiveis=true`: inclui registros sem referência de
  storage cujo metadado `indisponivel` é true. Padrão false preserva clientes antigos.
  Campo aditivo `indisponivel` na resposta; ausência no frontend equivale a false.
- Novo `GET /api/v1/leads/{leadId}/midias/contagem`: `{midias, documentos}`. Uma agregação
  SQL de metadados, incluindo os indisponíveis. Não depende da página carregada, nem
  retorna arquivo, referência de storage ou URL. Lead inexistente/inacessível retorna
  os dois totais zero, consistente com a listagem vazia sem revelar sua existência.
- Listagem e contagem compartilham o recorte de atendimento/mensagem sob RLS, pool CHAT,
  transação read-only e autorização existente. Nenhuma regra de Lead foi removida.
  OpenAPI documenta endpoint, categorias e parâmetro opcional.
- URL/Download continuam recusando mensagem sem referência de storage, inclusive por
  chamada direta. A seção indisponível mostra a mensagem catalogada, sem botão/preview.

## Carregamento, cache e recuperação

O cabeçalho usa uma consulta compartilhada de totais por lead. As listas só montam ao
abrir cada seção, com chaves distintas por lead e tipos. Página de 20 registros, como
antes; Carregar mais não refaz a contagem nem busca a outra categoria. Não apresenta
os 20 primeiros como total. A paginação existente pode confirmar o fim com uma página
vazia quando o total é múltiplo de 20; não oculta truncamento.

Envio de mídia confirmado invalida só `['lead', leadId, 'midias']`. O callback existente
do evento MENSAGEM faz o mesmo apenas para tipos de anexo; texto/reação não dispara essa
consulta. Reconexão STOMP revalida o lead selecionado. Não adiciona assinatura nem polling.
Uma fila por cache/lead espera leituras em curso antes de refazer a consulta: um evento
recebido durante a primeira leitura não pode deixar seu total antigo prevalecer. Eventos
duplicados nessa janela compartilham a mesma promessa; evento durante a leitura nova
agenda uma única rodada posterior. Sem timer, cancelamentos repetidos ou polling.
Seções fechadas não buscam páginas. O negativo com contagem atrasada falhou antes da correção.
Na abertura/troca da conversa, a invalidação normal compartilha a leitura recém-montada;
não agenda uma segunda contagem apenas por abrir a ficha. A fila é usada para eventos
de mídia e reconexões efetivas, que podem ter ocorrido depois do início da leitura.

Troca de lead troca as chaves e remonta as seções: não reutiliza itens, contadores ou
preview anteriores. O visualizador identifica o arquivo por mensagemId, não por índice:
uma mídia nova no começo da lista não muda o arquivo aberto. Erro na próxima página
preserva os itens carregados e oferece nova tentativa. Erro de totais não vira zero.

Títulos/estados novos têm defaults no TextosSchema para catálogos anteriores. A chave
legada `secoes.midias` permanece, sem forçar migração de catálogo ou tema.

## Download e defeitos encontrados

Na validação, o botão antigo da ficha abria um JPG em uma nova aba pela URL assinada,
em vez de iniciar um download. A ação agora reutiliza `apiFetchArquivo` e
`baixarBlobComoArquivo`, exatamente a infraestrutura JWT do menu da mensagem. Nome do
Content-Disposition tem precedência; na ausência, conserva o nome registrado no item.
Erros são catalogados e visíveis, e a trava impede cliques duplicados.

O Blob continua sendo o comportamento existente do download no navegador; não foi
introduzida outra conversão/buffer ou Base64. Os bytes só são solicitados no clique.
Preview/player continuam usando as URLs assinadas autenticadamente emitidas pelo CRM.

Outros achados não corrigidos aqui: warnings React de chaves duplicadas de lead na
inbox durante abertura por deep link/atualização canônica, fora das linhas desta tarefa;
logo não configurado retorna 404 e preserva o fallback. Não são descritos como regressão
corrigida por esta PR. A investigação do 410 Uzapi permanece separada (docs/52).

## Evidência e testes da implementação original (PR #310)

- Antes: dois testes novos de endpoint falharam (contagem 404 e indisponível ausente);
  o teste de duas seções independentes também falhou na interface anterior.
- Backend dirigido: AnexoMidiaIT 26/26 e OpenApiIT 8/8, incluindo contagem além da primeira
  página, documento PNG, categorias antes da paginação, JWT ausente, outro atendente,
  lead inexistente e indisponível sem URL/download. Preserva contratos legados e downloads.
- Frontend dirigido: 132 testes, incluindo interação, totais independentes das páginas,
  cache por lead/categoria, ausência de dados anteriores durante troca, invalidação,
  carregamento, erro e preview estável. Download JWT validado adicionalmente 12/12.
- Playwright CLI headed na aplicação real: 1920×1080 e 390×844, Inter carregado e fonte
  computada confirmada. Seções independentes; 25 documentos em páginas 20+5; a segunda
  página fez uma chamada de lista e nenhuma nova contagem. JPG/PNG permanecem Documentos.
- WAV e WebM sintéticos reproduziram no navegador. JPG baixado preservou nome e SHA-256
  dos bytes persistidos no MinIO. Envio pelo composer elevou Mídias 3→4 sem F5 e manteve
  Documentos 25. Troca pela lista para outro lead mostrou 0/0 e vazios específicos.
- Capturas em `evidencias/ficha-midias-documentos-20261015/`, produzidas primeiro em
  `output/playwright/`. Incluem categorias, paginação, preview, envio e troca de lead.

Resultados das suítes completas/CI acompanham o SHA na PR e no relatório final. Teste
dirigido local não é chamado de CI verde. Sem homologação em contas reais dos provedores.

## Desempenho e ambiente reproduzível

Contagem consulta somente metadados dos atendimentos visíveis do lead, uma vez por
invalidação, sem consulta global de histórico ou contagem por arquivo. As listas
preservam limites e índices existentes. EXPLAIN (ANALYZE, BUFFERS), sob `synapse_app` e
papel ATENDENTE na fixture: uso dos índices `atendimento_id/enviado_em` das partições;
29 anexos, Execution Time 0,526 ms / Planning Time 13,875 ms. É uma amostra pequena e
sintética, não benchmark da HML nem garantia de latência em produção. Nenhum índice foi
criado sem evidência; não foi feita medição na VPS.

`MidiasFichaLocal` existe só em test-classes e exige `MIDIAS_FICHA_LOCAL=true`. Sobe
Postgres `midias_ficha`, Redis e MinIO descartáveis, com o adaptador de storage real e
canal externo fake. Schedulers estão desligados: não entrega mensagens ao provedor.
Não toca nos containers compartilhados. Portas locais 8092/3012 nesta validação.
O seed verifica o nome do banco. O vídeo `frontend/e2e/fixtures/video-ficha.webm` é uma
gravação VP8 sintética de canvas no Chromium, sem cliente, câmera ou microfone reais.
Os demais arquivos da fixture são gerados em memória pelo próprio helper.

Skills clean-code/architecture-patterns/api-design-principles/supabaseboaspraticas não
estavam disponíveis no catálogo; Playwright foi lida antes da implementação/validação.

**Ação necessária no Dokploy: nenhuma.** Sem variável de produção nova. A revalidação não
executa merge ou deploy; o merge histórico da implementação está registrado acima.
