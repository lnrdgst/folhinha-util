# Validação técnica

Este documento complementa o [README](../README.md) com cobertura, reprodução e limites da validação.
Os resultados desta revisão foram obtidos em 24/10/2026, incluindo execução no Samsung com Android 16.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Testes JVM | 23 aprovados, sem falhas ou testes ignorados |
| Lint de debug | 0 erros e 15 avisos |
| Build | APK de debug e APK de testes gerados |
| Instrumentação no Samsung | 4 testes aprovados; 867 casos renderizados, sem falhas |
| Calendários reais | Leitura autorizada e coexistência com evento pessoal sintético conferidas |
| Cadastro real | Criação, edição, alternância anual e exclusão com confirmação exercitadas pela interface |
| Atualização do widget | Inclusão, edição e exclusão refletidas sem refresh manual |
| Persistência real | Cadastro e mês do widget preservados após reinstalação do APK e reinício do processo |
| Navegação na One UI | Setas e retorno ao mês atual conferidos; mês da tela principal independente |

Os avisos de lint abrangem versões disponíveis, atributos recentes do Android e sugestões de manutenção.
O relatório completo fica em `app/build/reports/lint-results-debug.html`.
Não foi executado reinício físico do Samsung nem alterado seu relógio ou fuso para simular a meia-noite.

## Testes JVM

[CalendarTests.kt](../app/src/test/java/com/folhinhautil/app/domain/CalendarTests.kt) preserva as verificações da grade,
dos 1.164 meses entre 2003 e 2099, categorias de feriados, anos bissextos, ordenação, dia inteiro e fusos.

[PersonalEventsTests.kt](../app/src/test/java/com/folhinhautil/app/domain/PersonalEventsTests.kt) cobre:

- Contagem em dias locais, hoje, amanhã, passado e virada de ano.
- Horário de verão com dia de 23 horas e mudança da data local conforme o fuso.
- Recorrência anual, data original futura e política de 29/02: 28/02 em anos não bissextos.
- Datas inválidas, ordenação das ocorrências e limites da navegação mensal.
- Dezembro/janeiro, estado mensal inválido e retorno à data atual.
- Exclusividade dos feriados nacionais, hoje e o ano seguinte.
- Categoria facultativa de 28/10/2026 e categoria comemorativa da Páscoa, sem conversão entre categorias.
- Reserva de uma posição para cada origem de evento e omissão de cópias exatas de feriados nacionais de dia inteiro.

## Testes instrumentados

O [runner](../app/src/androidTest/java/com/folhinhautil/app/widget/WidgetLayoutInstrumentation.kt) usa instrumentação
do framework, `RemoteViews` reais e `AppWidgetHostView`, sem acrescentar bibliotecas de testes Android.

- CRUD e reabertura de um DataStore isolado, inclusive persistência da exclusão.
- Preferências Glance independentes por identificador e reabertura do estado persistido.
- Matriz de layout e verificação de dados reais, esta última opcional e somente de leitura.

A matriz combina três meses com quatro, cinco e seis semanas; escalas de fonte 1,0, 1,15, 1,3 e 1,5;
e zero, um ou dois eventos de cada origem. Inclui as seis variantes e a dimensão 401,33 × 453,87 dp.
São 756 combinações, mais 108 renderizações da variante 360 × 440 dp na área real do launcher,
e um caso sem feriados futuros no limite do catálogo: 865 renderizações sintéticas.
A conferência opcional das agendas reais acrescenta duas, totalizando 867.

São verificados dias, finais de semana, marcadores, cores semânticas, legenda em uma linha,
seções presentes somente quando preenchidas, textos dos eventos, limites, sobreposição e ocupação vertical.
O log do processo é inspecionado por truncamentos de `Column` e `Row`, sem publicar seu conteúdo.
O Samsung estava com ajuste de peso de fonte 300; a medição considera esse negrito de acessibilidade.

As fixtures não são inseridas no Calendar Provider. O teste de persistência usa arquivo separado no cache.
O teste de estado usa contexto com diretório isolado e identificadores sintéticos.
A reflexão necessária para construir esses identificadores no Glance 1.1.1 existe somente no teste.
Na interface real foi criado um registro identificado como validação, removido ao concluir os testes.
Capturas exportadas automaticamente usam exclusivamente fixtures.

### Reprodução

Prepare o ambiente conforme [Executando o projeto](../README.md#executando-o-projeto).

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
```

Para acrescentar a conferência das agendas já autorizadas e selecionadas no aparelho:

```powershell
adb shell am instrument -w -r -e checkDeviceWidget true com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
adb uninstall com.folhinhautil.app.test
```

A conferência adicional informa somente se existem compromissos válidos; não exige um título específico.
Os 867 casos são renderizações, não 867 métodos de teste. Sem a opção adicional são três testes e 865 casos.
O APK auxiliar pode ser removido também após a execução padrão.

## Decisões de manutenção

- Cada instância mantém o mês em Preferences Glance; callbacks atualizam o estado e solicitam sua recomposição.
- Eventos pessoais usam Preferences DataStore, com JSON estruturado e versão de esquema 1.
- Os dados são locais e independentes das agendas Android. Não há cópia automática nem escrita no Calendar Provider.
- O aplicativo desabilita backup; desinstalação apaga os registros pessoais.
- A composição observa o fluxo de eventos pessoais para refletir alterações também em sessões Glance ativas.
- Mudanças de data, hora e fuso invalidam a composição para recalcular as ocorrências, preservando o mês escolhido.
- A altura dos textos considera SP, escala de fonte, peso de acessibilidade e arredondamento em pixels.
- A grade tem prioridade; depois vêm até dois feriados nacionais e os eventos que couberem, com títulos em até duas linhas.
- A legenda extensa só é usada quando cabe; a versão curta mantém as mesmas categorias e cores.
- Nenhuma `Column` ou `Row` deve exceder dez filhos diretos; funções composable não criam contêineres automaticamente.
- `LocalSize` em modo responsivo é uma variante predefinida. O host pode oferecer uma área maior.
- O teste deve preservar os parâmetros de layout do `RemoteViews`; medir diretamente o filho com altura exata pode ocultar falhas.

Não há garantia de atualização pontual à meia-noite: o Android e a economia de bateria podem adiar o trabalho.
Meses de seis semanas com fontes grandes podem deixar espaço somente para o calendário e os feriados.
Foram inspecionadas imagens sintéticas produzidas no Samsung, incluindo os formatos compacto, vertical e fonte ampliada.

## Atualização das agendas Android

O mecanismo de leitura e atualização do Google/Android foi preservado.
O aplicativo reconsulta ao retornar ao primeiro plano, pelo refresh e por mudanças observadas no Calendar Provider.
A consulta por minuto ocorre somente enquanto a atividade está em primeiro plano.
O widget solicita atualização periódica a cada hora, respeitando o agendamento do Android.

Permanece um risco específico dos snapshots de calendários:
`provideGlance` carrega o snapshot antes de `provideContent`, e uma chamada de `updateAll`
não reinicia necessariamente esse carregamento se a sessão estiver ativa.
Dados das agendas podem permanecer anteriores temporariamente; essa investigação continua separada.
A observação do DataStore de eventos pessoais não refatora esse mecanismo.

Em uma validação específica dessa integração, conferir alterações de permissão, seleção de agendas e eventos
durante e após uma sessão Glance ativa. Não foi repetida toda essa bateria manual neste marco.
Não forçar a parada como simulação de segundo plano normal, pois isso afeta o agendamento do Android.
Não publicar títulos, contas, dados pessoais ou capturas privadas em logs e documentação.
