# Prompt E06 — Tempo real (WebSocket + Redis)

> Pré-requisito: E05 commitada (`defbb09`). **Sessão limpa.**

---

**Etapa E06 — WebSocket com backplane Redis, para o chat parecer instantâneo em múltiplas instâncias.**

## 0. Ajuste pendente da E05: payload do webhook vira `TEXT`

Você identificou que JSONB normaliza o JSON (reordena chaves, insere espaço após `:`), então o que está gravado não é byte a byte o que chegou — e a assinatura HMAC não pode ser reconferida.

**Decisão: mude para `TEXT`.** Migration de uma linha agora.

Dois motivos:

- **Reprocessamento com reverificação** deixa de ser impossível. Se um bug no tradutor exigir replay dos webhooks, você quer saber que o payload é autêntico, não confiar que foi verificado meses atrás.
- Um registro de auditoria que diz "isto foi o que recebemos" e guarda algo diferente é uma mentira silenciosa — a categoria de problema que já mordeu este projeto três vezes.

O que se perde: operadores JSONB para consultar dentro do payload. Não é perda real — é log de webhook cru, não fonte de consulta.

---

## 1. A ameaça principal desta etapa

Toda a blindagem das E02, E02b e E03 protege o caminho HTTP. **O WebSocket é um segundo caminho de leitura, e ele não passa por nada disso.**

Um broadcast mal desenhado entrega mensagem de lead alheio a quem estiver escutando — e nenhuma Specification, política RLS ou trava de transação impede, porque nada disso está no caminho.

Trate a autorização de assinatura como o núcleo da etapa, não como detalhe:

- **Autenticar no handshake.** JWT validado ao abrir a conexão, não em cada frame.
- **Autorizar cada assinatura.** Antes de aceitar `/topic/atendimento/{id}`, verificar que o usuário enxerga aquele lead — pela mesma regra de visibilidade, não por uma cópia.
- **Reautorizar em transferência.** Quando um lead muda de dono, o atendente anterior deve parar de receber. Uma assinatura autorizada uma vez e nunca revista vaza a partir do momento da transferência.
- **Nunca confiar no cliente sobre em que ele está inscrito.**

Teste obrigatório, no mesmo espírito dos que expuseram os problemas anteriores: atendente A assina o tópico do lead de B e **não recebe nada**. E: A está legitimamente inscrito, o lead é transferido para B, A para de receber.

## 2. Backplane Redis

Pub/sub replicando eventos entre instâncias — sem isso, dois atendentes em instâncias diferentes não se enxergam.

- Tópicos por atendimento, por usuário (notificações, badge) e de presença
- O publisher do Redis roda no contexto de serviço; a **autorização acontece na entrega ao cliente**, não na publicação
- Sem *sticky sessions* obrigatórias

## 3. Ciclo de entrega na tela

A E05 estabeleceu que o envio é honestamente assíncrono: a mensagem existe como `PENDENTE` antes de qualquer provedor vê-la.

Sua recomendação de optimistic UI está certa, e pelo motivo certo — não é que o envio seja lento (10–25 ms até a tela), é que a confirmação é outra coisa. Portanto:

- A tela renderiza imediatamente com o **estado real**: `PENDENTE` = relógio, `ENVIADO` = ✓, `ENTREGUE` = ✓✓, `LIDO` = ✓✓ azul, `FALHOU` = ⚠ com ação de reenviar
- Cada transição vai por WebSocket
- **`FALHOU` precisa ser visível e acionável.** Um ✓ mentiroso é pior que um erro honesto — o atendente precisa saber que a mensagem não saiu enquanto o cliente ainda está na conversa

Reduza `intervalo-ms` do publisher para 200 ms em desenvolvimento, para encurtar a janela do relógio. Em produção, decida com dados reais.

## 4. Reconexão sem perda

O ponto que mais quebra em produção e menos aparece em teste local.

- Cliente guarda o instante da última mensagem recebida
- Ao reconectar, busca por HTTP o que perdeu (`GET /api/v1/atendimentos/{id}/mensagens?desde=`), depois retoma o WebSocket
- Backoff exponencial na reconexão, com teto — mil clientes reconectando em sincronia após um deploy é um ataque contra você mesmo
- Indicador de "reconectando" na UI

Teste: derrubar a rede por 10 s durante uma conversa ativa e confirmar que nada se perde nem duplica.

## 5. Bulkhead

O WebSocket não pode dividir pool de thread com relatório nem com o publisher da outbox. Executor próprio — é a mesma lógica dos dois DataSources da E00.

## 6. Testes

- Duas sessões em instâncias diferentes recebem a mesma mensagem em < 1 s
- **Atendente não recebe evento de lead que não enxerga**
- **Transferência revoga a assinatura do dono anterior**
- Conexão sem JWT válido é recusada no handshake
- Reconexão após 10 s de queda: sem perda, sem duplicata
- Transição de status chega na tela
- Carga leve: 50 conexões simultâneas sem degradar o tempo de envio

## Definição de pronto

- [ ] Mensagem trafega entre instâncias por Redis em < 1 s
- [ ] Autorização de assinatura provada por teste negativo
- [ ] Revogação em transferência funcionando
- [ ] Reconexão sem perda
- [ ] Ciclo de entrega completo na tela, com `FALHOU` acionável
- [ ] Executor próprio para WebSocket
- [ ] CI verde

Commit: `feat: tempo real com websocket e redis`.

Ao terminar, me diga como você revoga a assinatura na transferência — se a resposta for "o cliente reassina", quero entender por que o servidor não fecha.
