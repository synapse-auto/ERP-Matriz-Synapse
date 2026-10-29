# Audio recebido sem EOS — 29/10/2026

## Evidencia e limite da conclusao

Na Femina, imagem implantada `2ac4314`, tres entradas AUDIO terminaram com
`MIDIA_RECEBIDA_INVALIDA`, sem etapa/HTTP. Os webhooks estavam persistidos, declaravam
`audio/ogg; codecs=opus`, nao informavam downloadError e usavam o host permitido
f004.backblazeb2.com. Nao foi necessario liberar outro host nem modificar CORS.

Uma consulta manual posterior ao ultimo arquivo retornou HTTP 200, audio/ogg, 4582 bytes,
OpusHead versao 1/mono/48000/mapping 0, quatro paginas completas e granule final 101112,
mas nenhuma flag EOS na ultima pagina. A coleta ampliada confirmou CRC valido em todas as
paginas, sequencia contigua, stream unico, ausencia de continuacoes e pacotes encerrados.
Isso reproduz uma condicao rejeitada pelo validador
anterior. Nao existe hash/HTTP historico para provar que os bytes eram identicos na tentativa
original; os outros dois arquivos nao foram inspecionados integralmente.

## Correcao restrita

Somente a entrada AUDIO da Z-API pode aceitar o perfil sem EOS. Alem do caminho estrito
existente, o perfil exige CRC correto em todas as paginas, um unico stream, sequencia
contigua desde zero, BOS inicial, nenhum EOS intermediario/continuacao, pacotes encerrados,
OpusHead mono/48000/mapping 0, OpusTags com comprimentos validos e granule final positivo
sem regressao. Nao converte nem acrescenta EOS: storage/download preservam os bytes.

Uploads, documentos, outros provedores e o validador compartilhado continuam inalterados.
Host exato/HTTPS/DNS publico/sem redirect, limites, bulkhead, circuit breaker, processamento
assincrono, storage privado e autorizacao de download permanecem.

CRC/sequencia detectam corrupcao ou paginas intermediarias ausentes. Sem EOS nao e possivel
provar que o fornecedor nao omitiu paginas finais inteiras; nao se afirma integridade semantica
ou decodificacao completa do audio. Perfis fora desse recorte continuam indisponiveis.

## Diagnostico e operacao

O marcador MIDIA_NAO_RECEBIDA agora diferencia codigos fixos de host, referencia, tamanho,
conteudo vazio, OGG, tipo e processamento. HTTP permanente preserva etapa=download e http_status.
Falhas temporarias conservam backoff; storage/persistencia falham no processamento transacional,
nao se deve interpretar genericamente toda falha como erro do provedor. Nunca imprimir URL,
payload, excecao bruta ou tokens. O coletor manual le apenas uma referencia conhecida,
fixa DNS publico e TLS no host observado, nao segue redirects nem persiste o arquivo.

Nenhuma env/migration nova. Nao ha replay automatico ou recuperacao de arquivos historicos.
Depois de release autorizada, homologar um novo audio do proprio contato, incluindo reproducao,
download autorizado e recarga. O sucesso de fixtures/CI nao substitui essa homologacao.

Fonte: [RFC 7845](https://www.rfc-editor.org/rfc/rfc7845.html), organizacao de pacotes,
granule e cabecalho Opus. Ausencia de EOS e compatibilidade de entrada nao conforme,
nao uma afirmacao de que o fornecedor entrega um stream OGG completo conforme a RFC.
