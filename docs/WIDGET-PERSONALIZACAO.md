# Virada de mês e personalização — 01/11/2026

## Diagnóstico e atualização

O estado anterior persistia `viewed_month` indefinidamente. O broadcast de data alterava apenas
`clock_revision`: uma atualização podia redesenhar outubro em novembro. Agora cada instância
salva também `current_month_anchor`, o mês civil no qual a navegação foi realizada.
Enquanto essa referência é o mês atual, a navegação manual permanece. Quando muda, o widget
volta ao novo mês atual. Referências ausentes na migração também voltam ao mês atual uma vez.

A resolução ocorre na renderização, antes de navegar e na atualização periódica. Há broadcast
de data/hora/fuso, alarme inexato no próximo início de dia local, atualização do provider a cada
30 minutos e trabalho periódico persistente com WorkManager. Reinício e atualização do APK
rearmam o alarme. Uma sessão ativa também observa a data local a cada 30 segundos.
O Android/Doze e as restrições da Samsung podem adiar essas execuções: não se promete atualização
exatamente à meia-noite. A primeira execução posterior corrige o mês, hoje e as contagens.
Não há solicitação de alarme exato nem alteração de relógio/fuso.

## Configuração por instância

Abra **Personalizar widgets** no aplicativo e escolha o número do widget. Launchers compatíveis
também oferecem **Configurações** ao manter pressionado o widget. A configuração inicial é opcional.

- Zero, uma ou duas categorias, na ordem escolhida; use Subir/Descer para inverter duas seleções.
- Padrão: Feriados nacionais, depois Pontos facultativos e outras datas.
- Preferência independente para mostrar as quatro fases principais da Lua.
- Salvar grava somente as preferências da instância e solicita sua atualização imediatamente.
- Eventos pessoais continuam exclusivamente no rodapé, com até dois registros e a mesma apresentação.

Os três próximos nacionais são calculados pelo provider existente desde o primeiro dia do mês
visualizado, avançando meses/anos quando necessário. A seleção original de outras datas e suas
classificações foi mantida; a apresentação é limitada aos três primeiros registros desse conjunto
ordenado, exclusivamente do mês exibido. `WidgetHolidays.kt` e as regras legais não foram alterados.

As categorias conservam a ordem também na apresentação empilhada em widgets estreitos.
A medição considera escala tipográfica, negrito de acessibilidade e arredondamento do RemoteViews.
Preserva primeiro o rodapé pessoal que couber, representa as categorias e acrescenta registros
completos até três por categoria. Quando a lista completa de nacionais não cabe, permanece o
aviso acessível de ampliação com as três datas/nomes. Não se cortam títulos da lateral.
Widgets pequenos podem apresentar menos registros; títulos pessoais mantêm o limite original de duas linhas.

## Calendários e Lua

`CalendarRepository.load(month)` e a seleção existente de agendas são reutilizados sem alteração.
As ocorrências já expandidas pelo Calendar Provider são filtradas pela interseção real com o mês local,
ordenadas e deduplicadas por evento/instante. Eventos que atravessam a fronteira são incluídos somente
se ocorrem dentro do mês exibido. Dia inteiro respeita a data civil UTC do CalendarContract.
São exibidos título e descrição existente com data/horários ou Dia inteiro. Cópias de dia inteiro com
mesma data e título normalizado das datas exibidas no catálogo são excluídas da categoria Agenda.
Ausência de permissão, erro de consulta e mês sem compromissos têm mensagens específicas.
Dados da agenda não são gravados nas preferências nem nos logs.

As fases usam `MoonPhases.forMonth(month, ZoneId.systemDefault())`, sem modificar o algoritmo.
Quatro vetores pequenos, com a mesma convenção do hemisfério sul da interface existente, ficam
abaixo dos números. Os indicadores de feriado/facultativo continuam junto ao número.
O tamanho lunar adapta entre 4 e 8 dp e os ícones possuem descrição acessível. No menor formato,
com seis semanas e fonte ampliada, os números adaptam de 13 até 11 sp para reservar a faixa
lunar sem invadir a semana seguinte. A escala de acessibilidade continua sendo aplicada.

## Pendência meteorológica

`SidebarCategory.WEATHER` existe na estrutura, mas está indisponível na configuração e é rejeitada
na leitura de preferências. Não foi adicionada API, chave, permissão de localização ou acesso à rede.
Uma entrega futura deve implementar um repositório meteorológico real, origem/localidade dos dados,
atualização/cache e estados de indisponibilidade antes de habilitar essa opção.

## Validação desta entrega

| Verificação | Resultado em 01/11/2026 |
| --- | --- |
| JVM | 55 testes aprovados, zero falhas/erros/ignorados |
| Lint debug | Zero erros; 17 avisos de manutenção e compatibilidade |
| Build | assembleDebug e assembleDebugAndroidTest aprovados |
| Samsung SM-S918B / Android 16 | APK instalado por ADB com preservação dos dados |
| Estado e preferências por widget | Aprovado; DataStore isolado, virada outubro/novembro e recuperação em dezembro |
| Persistência de eventos pessoais | CRUD/reabertura isolados aprovados; registros reais conferidos sem alterações |
| Matriz de widget | 1.167 renderizações aprovadas; inclui 240 combinações de categorias/Lua e duas leituras do dispositivo |
| Interface lunar existente | 108 verificações aprovadas nas escalas 1,0 / 1,3 / 1,5 |
| Seletor de datas existente | Falhou ao aguardar o estado selecionado na árvore de acessibilidade; reproduzido isoladamente |

A execução completa teve cinco de seis métodos aprovados. O método personalEventDatePicker
não concluiu: PersonalDateDeviceChecks.kt:45 aguarda o dia selecionado no diálogo de 27/12/2026
e encerra por timeout de acessibilidade. O limite original de cinco segundos foi mantido.
A implementação e o teste auxiliar do seletor foram preservados, assim como cadastro e persistência.
O runner passou a informar a origem da falha em testes sintéticos. Essa falha impede declarar
aprovação integral da suíte instrumentada, mas não ocorreu na matriz do widget.

A matriz básica contém 925 renderizações; a configuração acrescenta 240; a conferência do dispositivo,
duas. Foram inspecionadas visualmente prévias sintéticas de novembro com feriados/agenda e rodapé,
além de agosto com seis semanas e fonte 1,5. Não houve truncamentos Glance, sobreposições ou textos
cortados nos casos aprovados. Títulos pessoais conservam as duas linhas originais.

O APK atualizado está em app/build/outputs/apk/debug/app-debug.apk e foi instalado no Samsung.
Os resultados históricos em VALIDACAO.md e WIDGET-DATAS.md referem-se às entregas anteriores.
A virada foi simulada por parâmetros de data; não foram realizados espera até meia-noite,
reinício físico nem alteração do relógio/fuso do Samsung. Restrições reais de Doze podem adiar a atualização.

Reprodução:

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e checkDeviceWidget true com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
~~~

Use -e section widget para a matriz/estado/persistência, -e section configuration para as 240
combinações, -e section moon para a interface lunar ou -e section date para reproduzir a falha de datas.
A instrumentação foi executada diretamente por ADB, com RemoteViews reais no aparelho.

A instrumentação usa datas injetadas e armazenamento em cache isolado para testar a virada de mês,
preferências e eventos pessoais. A matriz inclui meses de quatro/cinco/seis semanas, zero/uma/duas
categorias em ambas as ordens, Lua visível/oculta e fontes ampliadas. Não altera o relógio do Samsung,
o Calendar Provider nem os registros pessoais reais.
