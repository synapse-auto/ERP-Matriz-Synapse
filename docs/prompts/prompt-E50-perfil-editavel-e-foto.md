# Prompt E50 — campos do perfil editáveis e foto de usuário

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> Referência visual: `design/componentes/Configuracoes.html`.

A E49 entregou a tela de Minha conta, mas com telefone e cargo **somente leitura** — e não existe
nenhuma outra tela onde alguém possa preenchê-los. São duas colunas condenadas a ficar `NULL` para
sempre e dois campos vazios que ninguém entende. Esta etapa resolve isso e acrescenta a foto.

---

## Bloco 1 — Telefone e cargo editáveis pelo próprio usuário

Diretos: o usuário edita os dois na própria tela, junto com o nome, no mesmo `Salvar perfil`.

- São dados de exibição para a equipe. Não têm regra de negócio, não afetam autorização, não entram
  em nenhuma decisão do sistema.
- Autorização continua **no caso de uso**, sobre o **próprio** registro — o endpoint não recebe id.
- Telefone: guarde o que a pessoa digitou, sem inventar validação de operadora. Se o projeto já tem
  normalização de telefone (há `V24__telefone_canonico` e `V26__telefone_com_ddi_padrao` para lead),
  **verifique antes se ela se aplica aqui** e diga no relatório. Não duplique regra de telefone.

## Bloco 2 — E-mail: uma decisão, não um campo

O protótipo mostra o e-mail editável. **O e-mail é a identidade de login deste CRM.** Quem troca o
e-mail troca a chave da conta — e se alguém pegar uma sessão aberta numa máquina de balcão, trocar o
e-mail é como trancar o dono para fora.

**Implemente assim:** o campo fica editável, e a troca **exige a senha atual** na mesma requisição.
É a proteção padrão, é barata, e resolve exatamente o cenário da máquina compartilhada.

Exigências:

- Senha atual conferida **no servidor**, no caso de uso, nunca no frontend.
- E-mail novo não pode colidir com outro usuário — a recusa vem do banco (índice único), não de uma
  consulta prévia que perde a corrida.
- A troca de e-mail é **evento de auditoria**: quem trocou, quando, de qual para qual.
- As sessões ativas continuam válidas; não invente logout global nesta etapa.

Se você concluir que isso não cabe sem confirmação por e-mail de verdade, **pare e relate** em vez de
entregar uma versão frouxa.

## Bloco 3 — Foto de perfil

Não existe upload de foto de usuário hoje. Este é o bloco grande da etapa.

### Onde a foto aparece — leia antes de codar

`components/ui/avatar-iniciais.tsx` (`AvatarIniciais` + `tomDoAvatar`) é usado em **Agenda, Equipe,
Lembretes, Mensagens Programadas e Mensagens Rápidas**, e o mesmo padrão de iniciais aparece no
rodapé da barra lateral e no chat interno. **Ele não suporta foto.**

Se você tratar a foto só na tela de Configurações, a pessoa troca a foto e ela não aparece em lugar
nenhum do produto. **Estenda `AvatarIniciais` para aceitar a foto e cair nas iniciais coloridas
quando não houver** — assim a mudança vale em todas as telas de uma vez, sem duplicar componente.

### Armazenamento e entrega

- Guarde o arquivo no MinIO que já existe. **Não confunda com mídia de conversa**: prefixo/bucket
  próprio, ciclo de vida próprio.
- **Entrega pela aplicação, não por URL assinada.** O projeto já tem esse padrão: a logo da instância
  é servida por `/api/v1/config/logo`. URL assinada de 5 minutos (`MIDIA_S3_EXPIRACAO_LEITURA`) é
  errada para um avatar que fica na tela o dia inteiro.
- Coluna nova em `usuario` para a referência do arquivo, em **migration nova**.
- `GET /me` passa a devolver a foto.

### Validação — aqui mora o risco

- **Aceite apenas raster: JPEG, PNG e WebP.** **Nunca SVG.** SVG é documento executável; servido
  inline vira XSS armazenado, e um avatar aparece em todas as telas para todos os usuários.
- **Não guarde o arquivo como veio.** Reprocesse no servidor: recorte quadrado, redimensione para um
  tamanho fixo modesto (o avatar aparece entre 32 e 80 px; algo como 256 px de lado basta e sobra),
  reencode e **descarte os metadados** — foto de celular carrega GPS.
- Limite de tamanho no upload, recusado com mensagem clara, e o limite vem de configuração como os
  outros (`anexo.tamanho_maximo_imagem_mb` é de conversa — decida se reusa ou cria chave própria, e
  diga qual).
- Confie no **conteúdo**, não na extensão nem no `Content-Type` enviado pelo cliente.

### Comportamento

- Trocar a foto e **remover a foto** (voltando às iniciais coloridas). Sem remoção, uma foto ruim é
  permanente.
- O usuário mexe **na própria** foto. Trocar a de outro é da tela de Equipe e não entra aqui.
- Enquanto envia: estado de carregamento; se falhar, a foto antiga continua valendo.

## Bloco 4 — O que NÃO entra

- Preferências gerais, Aparência e Ajuda e suporte continuam fora (E49 explicou o porquê de cada).
- Não mexa em papel, que continua somente leitura.
- Não crie tela de administrador para trocar foto de terceiros.

---

## Verificação

- `npm test -- --run`, `npm run lint`, `npm run build`.
- Backend: `./mvnw clean verify` **com testes**, reator inteiro.
- Teste de que o usuário não altera o registro de outro — recusado no servidor.
- Teste de que a troca de e-mail **sem a senha correta é recusada**, e que e-mail duplicado é
  recusado pelo banco.
- **Teste de que upload de SVG é recusado**, e de que um arquivo com extensão de imagem mas conteúdo
  de outro tipo também é.
- Teste de que a imagem gravada foi reprocessada — não é o byte-a-byte do que subiu.
- Teste de que remover a foto volta às iniciais.
- **Verificação visual obrigatória**: troque a foto e confirme que ela aparece **também** na barra
  lateral e em pelo menos duas outras telas que usam `AvatarIniciais`. Se não conseguir subir o
  ambiente, **diga em letras claras**.
