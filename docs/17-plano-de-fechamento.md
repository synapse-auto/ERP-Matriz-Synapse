# 17. Plano de fechamento — 25/08

Escrito em 14/08/2026. **Onze dias.** Desenvolvedor solo, com agentes.

Este documento existe para que o que sobrar de fora em 25/08 seja escolha registrada, não consequência de esquecimento.

---

## 1. O que não é negociável

Nada disto é prompt. É tempo do Marcondes, e nenhum anda enquanto ele faz outra coisa. Somados, são umas quatro horas espalhadas — mas três dependem de terceiros e por isso precisam começar cedo.

| # | Item | Depende de | Por que não é adiável |
|---|---|---|---|
| 1 | **Mensagem real no WhatsApp, nos dois sentidos** | tokens (já chegaram) | é o único teste que prova o caminho de mensagens no ambiente hospedado. Este projeto já teve esse caminho quebrado nos dois sentidos por duas etapas, com CI verde |
| 2 | **Smoke RLS** | nada | se um atendente enxerga lead de colega, é comissão de gente real. E o conserto pode mexer em migration, colidindo com o que os agentes estiverem construindo |
| 3 | **Etapas do funil e tags reais** | subgestora | sem elas o sistema sobe com dado de exemplo, e a tela inteira parece errada para quem for homologar |
| 4 | **Backup restaurado de verdade** | bucket S3 | backup nunca restaurado é esperança, não backup |
| 5 | **Watchdog provisionado + teste destrutivo** | VPS de outro provedor | alerta que nunca disparou não é alerta. É o décimo terceiro caso do mesmo padrão |
| 6 | **Subdomínios reais** | quem tem o DNS | o `sslip.io` divide cota do Let's Encrypt com o mundo. Na renovação o certificado pode não sair, e aí a Meta para de entregar webhook |

**Ordem de disparo:** 3 e 6 hoje, porque dependem de outra pessoa responder. 1 e 2 na primeira meia hora livre. 4 e 5 até 20/08.

## 2. O que construir, em ordem

| Ordem | Item | Tamanho | Por quê aqui |
|---|---|---|---|
| 1 | **Testes das três rotas devolvidas ao `docs/04`** — tema, textos, `GET /usuarios` | trivial | a regra de evidência criou um ponto cego: rotas reais que agora não estão documentadas em lugar nenhum |
| 2 | **Mensagens rápidas compartilhadas** (`atendente_id` nulo = da equipe) | pequeno | resposta pronta compartilhada é das coisas mais úteis numa operação com vários atendentes, e o protótipo mostra o grupo |
| 3 | **Modal de tag com a paleta do protótipo** | pequeno | confirmar o conjunto com o cliente antes |
| 4 | **Horários de trabalho** | 1,5 a 2 dias | módulo inteiro. Hoje a disponibilidade do atendente é manual — ninguém entra em expediente sozinho |
| 5 | **Regras de automação** | grande | ver a pergunta abaixo |

Do 1 ao 4 cabe com folga. O 5 é o que precisa de decisão.

## 3. A pergunta que decide as regras de automação

As tabelas de `regra_follow_up`, `regra_fidelizacao`, `mensagem_festiva` e `configuracao_resumo_ia` existem, sem nenhum caso de uso. Ninguém consegue configurar nada disso pelo CRM.

**A pergunta é para o Dylan:** os workflows dele vão **ler essas regras do CRM**, ou vão codificá-las dentro do n8n por enquanto?

- Se lerem do CRM → o item 5 entra, e é a maior coisa que resta
- Se ficarem no n8n → o item 5 vai para fase 2 legitimamente, e o gestor configura automação falando com a Synapse até lá

Não dá para decidir isso do lado do CRM sozinho. **Pergunte hoje** — a resposta muda o que cabe nos onze dias.

## 4. Reserva de dois dias

**23 e 24/08 ficam livres, de propósito.** Assim que o Lucas e a subgestora testarem, volta uma lista — e ela precisa caber em algum lugar. Enfileirar etapa nova até o dia 24 significa escolher entre ignorar o feedback ou desfazer trabalho recém-feito.

Se ninguém achar nada, esses dois dias viram folga. É o melhor problema possível.

## 5. O que fica de fora, e o que dizer ao cliente

Precisa ir **por escrito**, antes de alguém procurar no menu:

| Item | Quando |
|---|---|
| Dashboard — abas Operacional, Comercial, IA & Automação | fase 2 |
| Relatórios, Campanhas, Banco de Arquivos, Chat interno | fase 2 |
| Kanban na Agenda | fase 2 — sem endpoint de agrupamento |
| Importar/exportar CSV | fase 2 — sem motor CSV dos dois lados |
| Avaliação por atendimento na Automação | fase 2 |
| Troca de credencial do canal pela gestão | fase 2 |

E, se o item 4 da seção 2 não couber: **disponibilidade do atendente é manual nesta versão.** Não é detalhe técnico — muda a rotina de quem usa, e é uma frase de dez segundos que evita uma reclamação.

## 6. Duas decisões de produto ainda abertas

**Escala do CSAT.** O banco guarda 0–5; o protótipo aprovado mostra "9,4 / 10". A pergunta que decide: o que a Automação vai perguntar ao cliente final? Se for 1 a 5 estrelas, o protótipo está errado. Se for 0 a 10, o banco muda — e é infinitamente melhor mudar agora, com o sistema vazio, do que depois com avaliação real dentro.

**Conjunto de cores e ícones de tag.** 7 tons e 22 ícones no protótipo, 7 e 14 no construído.

## 7. Dívidas com data, para depois de 25/08

- **PITR no Postgres**, antes do go-live de produção (`docs/10` §1.1b). `pg_dump` horário significa perder até uma hora de conversa num incidente — aceitável em homologação, não com o cliente operando
- **Limites de mídia** conferidos contra a documentação atual da Meta
- **Segundo filho** — é ele que valida o modelo Base PAI de verdade, não uma instância genérica vazia
