# Validação técnica

Este documento complementa o [README](../README.md). Revisão de três feriados e fases da Lua: 25/10/2026.

A revisão posterior da coluna lateral e do seletor de datas, com resultados próprios, está em [WIDGET-DATAS.md](WIDGET-DATAS.md). Os resultados lunares abaixo registram a rodada anterior.

## Resultados automatizados

| Verificação | Resultado |
| --- | --- |
| Testes JVM | 44 aprovados, sem falhas ou testes ignorados |
| Lint de debug | 0 erros e 15 avisos preexistentes |
| Build | APK de debug e APK auxiliar gerados |
| Widget no Samsung Android 16 | 3 testes aprovados; 805 casos renderizados, sem falhas |
| Interface lunar no Samsung | Parcial: fonte 1,0 concluída; teste de acessibilidade interrompido na escala 1,3. Layout aprovado pelo usuário; validação adicional dispensada |
| Dados reais | Nenhuma escrita em dados reais; testes desta rodada usam fixtures e armazenamento isolado |

Os avisos de lint incluem versões disponíveis, atributos recentes do Android e sugestões de manutenção.
Relatórios: `app/build/reports/tests/testDebugUnitTest/index.html` e `app/build/reports/lint-results-debug.html`.

## Cobertura JVM

- [CalendarTests](../app/src/test/java/com/folhinhautil/app/domain/CalendarTests.kt): grade, 1.164 meses entre 2003 e 2099,
  finais de semana, bissextos, categorias de feriados e compromissos com horários, dia inteiro e fusos.
- [PersonalEventsTests](../app/src/test/java/com/folhinhautil/app/domain/PersonalEventsTests.kt): dias corridos locais,
  hoje, passado, virada de ano, horário de verão, recorrência anual e política de 29/02.
- [WidgetHolidaysTests](../app/src/test/java/com/folhinhautil/app/domain/WidgetHolidaysTests.kt): referência mensal dos
  feriados, outubro/novembro/dezembro, dezembro/janeiro, meses anteriores e sem feriados nacionais, limites de anos,
  uso do provider existente, três primeiros nacionais desde o início do mês, sem duplicação, busca em anos posteriores, seleção das duas últimas datas de cada grupo amarelo e exibição em ordem crescente.
  Também cobre legendas vermelha, amarela, ambas e nenhuma, sem mudar categorias.

- [MoonPhasesTests](../app/src/test/java/com/folhinhautil/app/domain/MoonPhasesTests.kt): 347 instantes oficiais USNO/NASA (2003, 2024, 2026, 2027, 2028, 2050, 2099), oito fases, transições, fusos, bissextos e todos os meses de 2003–2099 em São Paulo e Nova York. Maior erro observado: 45,082 s; tolerância: 120 s.

## Instrumentação

O [runner](../app/src/androidTest/java/com/folhinhautil/app/widget/WidgetLayoutInstrumentation.kt) usa instrumentação
do framework, `RemoteViews` reais e `AppWidgetHostView`, sem novas dependências de testes.

1. CRUD e reabertura de DataStore em arquivo isolado no cache, sem acessar registros pessoais reais para escrita.
2. Estado mensal por instância, dezembro/janeiro, retorno ao mês atual e independência entre identificadores.
3. Matriz de composição responsiva e inspeção de truncamentos do Glance.
4. Grade e detalhes lunares em atividade exclusiva do APK debug, com dados sintéticos, sem repositório ou persistência: 31 datas e cinco seleções nas escalas de fonte 1,0, 1,3 e 1,5 (108 verificações).
5. Verificação opcional, somente de leitura, das agendas e da integridade dos registros pessoais existentes.

A matriz combina quatro escalas de fonte (1,0; 1,15; 1,3; 1,5), seis meses (fevereiro, abril, agosto, outubro,
novembro e dezembro de 2026), zero a dois eventos pessoais e nove dimensões.
As oito variantes incluem 400 × 450 dp, selecionada para a área de 401,33 × 453,87 dp do Samsung.
Inclui a renderização dessa variante dentro da área maior do launcher: 720 combinações.
Mais 12 casos verificam nomes extensos, 72 combinam seleções de 0, 1, 2, 3, 5 e 12 feriados com calendário de seis semanas, e um exercita o limite superior do catálogo: 805.
A conferência de dados reais acrescenta dois, totalizando 807 renderizações.

São verificados calendário completo, cores, marcadores, legendas condicionais, seleção dos três primeiros nacionais desde o primeiro dia do mês, aviso de espaço insuficiente com descrição acessível completa,
datas amarelas do mês, ordem crescente, contagens independentes da navegação, separadores e ocultação de seções vazias.
Os testes inspecionam limites dos textos e dos contêineres ancestrais, sobreposição, reticências indevidas e
mensagens de truncamento de `Column` e `Row`. Títulos pessoais podem usar até duas linhas, com reticências intencionais.

A construção dos identificadores sintéticos usa reflexão somente no teste, por limitação do Glance 1.1.1.
As preferências desses identificadores ficam em diretório de cache isolado.
Fixtures não são inseridas no Calendar Provider nem no DataStore usado pelo usuário.
As imagens exportadas automaticamente contêm exclusivamente dados sintéticos.

### Reprodução

Prepare o ambiente conforme [Executando o projeto](../README.md#executando-o-projeto).

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
```

Para acrescentar a leitura das agendas já autorizadas e selecionadas:

```powershell
adb shell am instrument -w -r -e checkDeviceWidget true com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
adb uninstall com.folhinhautil.app.test
```

Sem a opção adicional, são quatro testes: 805 renderizações de widget e 108 verificações da interface lunar. A checagem opcional acrescenta um teste e duas renderizações de widget.
O APK auxiliar pode ser removido também após a execução padrão.
Não confundir o número de renderizações com o número de métodos de teste.

## Decisões de apresentação

- O cabeçalho acompanha a área do calendário. Grade e feriados ficam lado a lado quando a largura permite.
- A lista inclui exatamente os três primeiros feriados nacionais desde o primeiro dia do mês exibido, mesmo quando já passaram em relação a hoje.
- A busca continua pelos anos seguintes até completar três, sem duplicação, respeitando 2099. No último mês do catálogo só há o Natal: não são inventados feriados de 2100.
- A coluna reduz primeiro os intervalos sem diminuir os números da grade. Se necessário, usa a lista abaixo do calendário.
- Os títulos “Feriados” e “Pontos facultativos e outras datas” foram removidos. As legendas e cores permanecem. As datas amarelas podem ocupar a direita, abaixo dos nacionais, separadas por linha sutil; se essa posição perder eventos pessoais ou não couber com legibilidade, ficam abaixo do calendário. No cenário Samsung de outubro com fonte 1,15, os dois eventos pessoais voltam a caber sem reduzir a fonte da grade.
- Quando a lista completa não cabe, o aviso “N feriados · amplie” ocupa a faixa inferior do calendário. Sua descrição acessível inclui todas as datas e nomes selecionados. Não há descarte silencioso de feriados.
- A lista é agrupada em contêineres com até sete filhos diretos, inclusive para seleções maiores que dez itens.
- A seção amarela seleciona as duas últimas datas facultativas e as duas últimas das demais categorias não nacionais
  do mês exibido. O conjunto é apresentado em ordem crescente, preservando as classificações.
- As legendas dependem dos indicadores do próprio mês, não das datas futuras mostradas na coluna lateral.
- O calendário tem prioridade. As seções inferiores mantêm ambas as origens de informação quando possível;
  depois preservam até dois eventos pessoais e encaixam as datas amarelas completas que couberem.
- Dimensões menores e fontes grandes podem reduzir as quantidades ou usar a disposição em uma coluna.
- A tipografia do calendário foi preservada. A medição considera escala e negrito de acessibilidade.
- Há reserva de dois dp na largura medida para evitar que arredondamentos independentes dos filhos do
  `RemoteViews` façam uma linha caber na medição e quebrar durante a renderização.
- Cada função de separador cria seu contêiner. Nenhuma `Row` ou `Column` ultrapassa dez filhos diretos.
- O host pode oferecer uma área maior que `LocalSize`; os testes preservam os parâmetros do `RemoteViews`.

## Evidências visuais no Samsung

Nesta rodada, foram inspecionadas as imagens sintéticas de outubro de 2026 com dois eventos pessoais e sem os títulos redundantes, novembro com três feriados e dois eventos, e formato compacto com aviso de espaço insuficiente. Também foi conferida uma captura lunar com fonte 1,3: grade, fase selecionada e feriado legíveis. O usuário aprovou o layout e encerrou a validação no aparelho. As imagens ficam em `app/build/qa/`, ignorado pelo Git.
A imagem `widget-all-holidays-november.png` é uma renderização instrumentada, não uma captura da One UI.
A captura real da tela inicial com a lista completa ficou pendente: o usuário estava usando o aparelho e
solicitou concluir com as imagens instrumentadas.

No marco anterior foram conferidos navegação e os quatro estados das legendas na One UI.
Essas capturas anteriores não comprovam a nova coluna com três feriados.
Nenhum registro pessoal real foi criado, editado ou excluído durante os testes desta correção.
## Persistência, atualização e limitações

A persistência dos eventos pessoais, o esquema JSON versão 1, a política anual e as preferências de navegação
não foram alterados nesta revisão. As contagens continuam usando a data local real.
O widget observa o fluxo de eventos pessoais e recebe invalidações em mudanças de data, hora e fuso.
O Android pode adiar trabalho periódico; não há garantia de execução pontual à meia-noite.
Não foram alterados o relógio ou o fuso do Samsung nem realizado reinício físico nesta revisão.

O acesso aos calendários Android permanece na tela principal, com os mesmos filtros e origem.
A nova apresentação do widget é exclusiva para feriados, outras datas do catálogo e eventos pessoais locais.
O repositório de calendários e as definições legais de feriados foram preservados.

Fica mantido como referência para investigação separada o risco de snapshots observado no widget anterior:
carregar dados antes de `provideContent` e chamar `updateAll` não garante nova consulta durante uma sessão ativa.
O widget atual não carrega snapshots de agendas Android; a nota deve ser considerada se essa apresentação
for reintroduzida. O mecanismo do CalendarRepository não foi refatorado.

Capturas reais devem permanecer locais quando contiverem informações pessoais.
Não publicar títulos, contas, registros pessoais ou logs privados no repositório.

## Execução desta revisão lunar

`connectedDebugAndroidTest --offline` não iniciou testes: o plugin UTP `android-test-plugin-host-additional-test-output:31.11.1` não estava no cache. Os APKs compilados são instalados com `adb install -r` e a mesma instrumentação é executada diretamente pelo ADB. Nenhum timeout foi ampliado para mascarar falhas. O widget passou em 805 renderizações, além dos testes de persistência isolada e navegação. A execução lunar passou pela escala 1,0, mas falhou na leitura de texto de fase após selecionar 12/10 com fonte 1,3: a captura visual mostrava “Lua crescente”, enquanto a árvore de acessibilidade ainda devolvia “Lua nova”. O teste recebeu atualização explícita do cache de acessibilidade, cuja reexecução no aparelho ficou pendente quando o usuário aprovou o layout e dispensou testes adicionais. Não se declara aprovação das 108 verificações lunares.

Para executar separadamente, acrescente `-e section widget` (três testes) ou `-e section moon` (um teste) ao comando de instrumentação.

A tela `MoonCalendarPreviewActivity` existe somente em `src/debug`, não é exportada e não acessa CalendarContract, DataStore ou preferências do usuário. Ela reutiliza a grade e os detalhes reais com fixtures de compromissos e feriados. Não integra a versão release.
