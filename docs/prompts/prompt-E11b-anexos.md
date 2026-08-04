# Prompt E11b — Anexos no chat

> Pré-requisito: E11 commitada (`19f9008`).
> **Necessário para a homologação.** O cliente é uma vidraçaria: sem anexo, o chat não serve para o negócio dele.

---

**Etapa E11b — Upload e envio de mídia, ponta a ponta.**

Dos cinco gaps deixados na E11, este é o único que não é conveniência: o atendente precisa mandar foto do vão, orçamento em PDF e imagem de produto. Um chat só de texto não é utilizável para uma vidraçaria.

É também o único que exige infraestrutura nova.

## 1. Storage

MinIO no `docker-compose` para desenvolvimento, S3-compatível em produção (ver `docs/10`). Configuração da instância, nunca hardcoded.

- Bucket por instância, definido em `application.yml`
- **URL assinada com expiração** para leitura — nada de bucket público
- Upload direto do backend, não do browser para o storage (o backend precisa validar antes)

## 2. Backend

`POST /api/v1/atendimentos/{id}/mensagens/midia` — multipart, retornando a mensagem criada.

Validações **antes** de gravar:

- **Tipo permitido por allowlist**, verificado pelo conteúdo real (magic bytes), não pela extensão nem pelo `Content-Type` que o cliente mandou
- **Tamanho máximo** configurável em `configuracao_automacao`, com o limite da Meta como teto (16 MB para vídeo, 5 MB para imagem, 100 MB para documento — confirme na documentação atual antes de fixar)
- Nome de arquivo sanitizado; nunca use o nome do cliente como caminho no storage

`mensagem.midia_metadados` (JSONB) guarda nome original, mimetype, tamanho e legenda. `midia_url` guarda a referência no storage, não a URL assinada — a assinatura é gerada na leitura.

O envio segue o mesmo caminho da mensagem de texto: outbox, publisher, adaptador da Meta. **Não crie um caminho paralelo.**

## 3. Recebimento

O webhook da Meta traz mídia por referência — é preciso baixar do provedor e persistir no seu storage. Se você guardar só a URL da Meta, ela expira e o histórico do cliente fica com anexos quebrados em algumas semanas.

O download vai para a fila, não para o caminho síncrono do webhook.

## 4. Frontend

- Botão de anexo no composer, com seleção de arquivo e preview antes do envio
- **Progresso de upload** — arquivo de 5 MB em conexão de vidraçaria não é instantâneo
- Card de anexo conforme `RF-CRM-68`: nome, metadados e ação de download; imagem com miniatura e legenda
- O mesmo ciclo de entrega da mensagem de texto: `PENDENTE` → `ENVIADO` → `ENTREGUE` → `LIDO` → `FALHOU` com reenviar
- Erro de upload é visível e acionável, não um toast que some

## 5. Testes

- Upload de imagem, documento e áudio, ponta a ponta
- Arquivo de tipo não permitido é rejeitado — **inclusive quando a extensão mente** (arquivo `.jpg` que na verdade é executável)
- Arquivo acima do limite é rejeitado antes de subir ao storage
- Mídia recebida por webhook é baixada e persistida, e o histórico continua acessível depois de a URL da Meta expirar
- URL assinada expira e não vaza acesso ao bucket
- Anexo em atendimento de colega não é acessível (o RLS cobre a mensagem, mas confirme que a URL assinada também exige autorização)

Esse último importa: uma URL assinada válida entregue à pessoa errada é um vazamento que o RLS não pega.

## Definição de pronto

- [ ] Atendente envia imagem, documento e áudio, e o cliente recebe
- [ ] Mídia recebida do cliente aparece na conversa e persiste no storage próprio
- [ ] Validação por conteúdo real, não por extensão
- [ ] Progresso de upload e erro acionável na UI
- [ ] Autorização na leitura do anexo, provada por teste negativo
- [ ] CI verde

Commit: `feat: anexos de midia no atendimento`.

Ao terminar, me diga qual o tamanho máximo que ficou configurado e se ele bate com os limites atuais da Meta Cloud API — esse é o tipo de número que muda e quebra em produção sem avisar.
