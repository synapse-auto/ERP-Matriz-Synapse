# Fixture Word vazia

`vazio.doc` deriva de `test-data/document/empty.doc` do Apache POI, referência
`cfc8f6d2619292fa4fa58292aba0153801b2c5b3` (27/10/2026), Apache License 2.0:
https://github.com/apache/poi/blob/cfc8f6d2619292fa4fa58292aba0153801b2c5b3/test-data/document/empty.doc

Foi reconstruída por POIFS copiando somente WordDocument, 0Table/1Table e Data.
Propriedades de autor/SummaryInformation, storages VBA e demais streams não foram
copiados. Não contém documento de cliente. A cópia em crm-app é idêntica e permite
os testes HTTP sem depender do diretório de trabalho nem de rede.

DOCX é gerado pelos testes com OPC/relationships/WordprocessingML sintéticos.
