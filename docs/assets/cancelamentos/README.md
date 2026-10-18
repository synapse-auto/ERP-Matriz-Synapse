# Validação local — Cancelamentos

18/10/2026. Aplicação Next.js real em `localhost:3019`, Spring Boot real em `localhost:8099`,
PostgreSQL 15 e Redis descartáveis por Testcontainers. Login pelo formulário do CRM com o
administrador público do seed de desenvolvimento. Não são telas HTML reproduzidas nem dados reais
da Fêmina. A marca visível é a configuração padrão do ambiente dev, não regra da nova aba.

Dados vêm de `FonteCancelamentosDeTeste` (`crm-app/src/test`), injetada somente no ambiente local
de teste. Não há mock de dados no frontend de produção, consulta ao n8n ou contato com pacientes.
Flag habilitada apenas no banco descartável para exercitar a tela; V108 permanece OFF.

## Capturas finais

- `cancelamentos-desktop-1920.png`: 1920×1080, tabela com registro/agendamento distintos, campos ausentes.
- `cancelamentos-desktop-1280.png`: 1280×900, filtros e ações contidos na viewport.
- `cancelamentos-filtros-1280.png`: nome + profissional + tipo combinados, um resultado no servidor.
- `cancelamentos-mobile-390.png`: 390×844, formulário com componentes reais do design system.
- `cancelamentos-mobile-lista-390.png`: 390×844, cartões com todos os campos e ação visível.
- `cancelamentos-detalhe-mobile-390.png`: detalhe contido na viewport, descrição com quebra de linha,
  fechamento e aviso explícito de vínculo indisponível.

Antes das capturas finais: `await document.fonts.ready`, `document.fonts.status = loaded`;
font-family computado `Inter, "Inter Fallback", Inter, sans-serif`, escala do dispositivo 1.
`document.documentElement.scrollWidth` = viewport em 1920, 1280 e 390; nenhum overflow horizontal
da página. A primeira comparação mobile revelou tabela com rolagem horizontal e nomes quebrados:
foi substituída por cartões somente abaixo do breakpoint estreito já existente. Essa captura
intermediária não é evidência final.

Interações verificadas no navegador headed: abrir detalhe por Enter, fechar por Escape, foco
retorna ao botão Ver detalhes; combinar filtros textuais e limpar (três registros novamente);
abrir calendário existente e selecionar data. Nada foi enviado a provedor nem reprocessado.
Autorização negativa, datas/fuso, falhas, estados vazio/não configurado e concorrência de respostas
também são cobertos por testes automatizados, não apresentados como homologação externa.

Limites do preview: logo privado não provisionado (fallback local existente) e proxy WebSocket
não preparado neste servidor Next de teste. Não afirmar validação de tempo real com fornecedor;
a aba não implementa polling nem depende de WebSocket. Logs brutos do navegador não são
versionados (podem incluir token descartável de sessão na URL do WebSocket).

Resultados completos e CI do último SHA constam no relatório da PR #313. Capturas não comprovam
instalação do SQL externo, valores reais, correlação de lead ou operação na Fêmina.
