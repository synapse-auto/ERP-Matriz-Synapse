# Menu de ações do composer

## Escopo

O composer do chat de atendimentos usa o clipe como ponto único para ações auxiliares. O popover
abre acima do composer e mostra somente ícones em uma grade. Cada ação tem nome no catálogo de
textos, tooltip e `aria-label`; os textos não ficam visíveis na grade.

As ações reais do composer são:

- **Arquivos:** abre o mesmo seletor de arquivos múltiplos. O `accept` vigente continua cobrindo
  imagens, áudio e documentos suportados, sem alterar o upload ou o contrato de mídia.
- **Templates:** abre o catálogo aprovado do WhatsApp e mantém as regras da janela de 24 horas.
- **Mensagens rápidas:** abre a lista existente de respostas pessoais; escolher uma resposta apenas
  preenche o textarea e não envia automaticamente.

O popover fecha ao escolher arquivo, abrir templates, escolher uma mensagem rápida ou clicar fora.
Fechar e reabrir não desmonta o textarea, a citação de resposta nem os anexos já preparados. As
ações usam áreas de clique maiores, navegação por foco e setas, além dos estados hover, focus e
disabled definidos pelos tokens e utilitários Tailwind existentes.

## Limites de produto

O microfone permanece como ação própria do composer porque grava áudio com confirmação e preview;
o fluxo não foi movido nem alterado. Não há ação de câmera porque essa capacidade não existe no
componente atual. O chat interno continua usando seu composer específico: ele não compartilha a
lista de mensagens rápidas nem uma toolbar de ações incompatíveis, conforme a regra do prompt E68.

Nenhum endpoint, payload, contrato de envio ou fluxo de citação foi alterado nesta reorganização.
