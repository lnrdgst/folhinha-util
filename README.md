# Folhinha Útil

MVP Android nativo de calendário pessoal, com aplicativo em Kotlin/Jetpack Compose e widget em Jetpack Glance. Application ID: `com.folhinhautil.app`. Sem backend, login próprio ou acesso à internet em tempo de execução.

## Requisitos

- Android 8.0 (API 26) ou superior, incluindo Samsung/One UI.
- Android Studio com suporte a AGP 8.11.1; JDK 17 ou 21.
- Android SDK Platform 36, Build Tools 35.0.0, Platform Tools.
- Gradle 8.14 pelo wrapper. Kotlin e compilador Compose 2.2.20; Compose BOM 2025.06.01; Glance 1.1.1. São versões estáveis, fixadas para compatibilidade com o SDK disponível.
- Internet somente para baixar ferramentas e dependências na primeira compilação.

O diretório inicial estava vazio. Foram encontrados JDK 17/21, SDK 36, Build Tools 35.0.0/36.1.0 e Gradle 8.14 no cache. `local.properties` aponta para o SDK desta máquina e é ignorado pelo Git. Não foram alteradas configurações globais. Nenhum aparelho estava conectado durante a inspeção inicial.

## Build e testes

No PowerShell, dentro deste diretório:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Em outro computador, abra a pasta no Android Studio e deixe-o localizar o SDK, ou crie `local.properties` com `sdk.dir=C\:/caminho/Android/Sdk`. Se necessário, configure o JDK apenas para a sessão com `$env:JAVA_HOME = 'C:\caminho\jdk-21'`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Relatórios: `app/build/reports/tests/testDebugUnitTest/index.html` e `app/build/reports/lint-results-debug.html`.

### Resultado da validação — 24/10/2026

`testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**. Os 11 testes JVM passaram, sem falhas ou testes ignorados. O teste de grade cobre todos os 1.164 meses entre 2003 e 2099. Lint: **0 erros e 14 avisos**, principalmente versões mais recentes disponíveis, atributos de widget usados apenas no Android 12+, sugestões de extensões Kotlin e configuração de backup. As versões atuais do projeto são estáveis e compatíveis com SDK 36; os avisos não foram desabilitados globalmente.

APK de debug gerado e conferido com `aapt`: nome Folhinha Útil, ID `com.folhinhautil.app`, mínimo API 26, alvo API 36. Permissões finais: `READ_CALENDAR`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` e a permissão interna de assinatura do AndroidX. Sem permissão de internet, rede, escrita na agenda ou serviço em primeiro plano. O APK tem aproximadamente 12,5 MiB.

Instalação confirmada com `adb install` e abertura da atividade principal confirmada com `am start -W` (`Status: ok`) no emulador Pixel 6 com Android 16/API 36, antes e depois de conceder READ_CALENDAR via ADB. A captura confirmou a renderização do calendário e do estado sem permissão, mas mostrou um aviso de travamento do **System UI do emulador**; a validação visual completa e a interação com o widget ficaram pendentes. O emulador temporário foi encerrado. Não houve validação em Samsung físico nem de sincronização com uma conta pessoal.

## Instalar e testar no Samsung

1. Conecte o aparelho por USB. Ative Opções do desenvolvedor → Depuração USB e aceite a chave do computador.
2. Execute `adb devices`. O aparelho deve aparecer como `device`, sem `unauthorized`.
3. Execute `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Abra **Folhinha Útil**. Confira o mês atual, botões de navegação, Hoje e seleção dos dias. Sábado e domingo ficam vermelhos; hoje recebe fundo verde, e o dia selecionado tem contorno.
5. Toque em **Permitir leitura da agenda** quando quiser mostrar compromissos. Selecione os calendários em **Agendas** e salve. Uma conta Google precisa estar adicionada ao Android com sincronização de calendário ativa. O app não faz login nem força sincronização.
6. Na tela inicial da One UI, pressione uma área vazia → Widgets → Folhinha Útil. Arraste o widget e redimensione-o. O formato compacto mostra o mês; o vertical acrescenta até três compromissos conforme o espaço e a fonte; o largo mostra os dois lado a lado. As dimensões efetivas dependem da grade do launcher.
7. Confira uma ocorrência recorrente real, um evento de dia inteiro e um evento que cruza a meia-noite. Altere a seleção de agendas e confira o widget. Remova a permissão em Configurações → Aplicativos → Folhinha Útil → Permissões e reabra o app para validar o estado sem acesso.
8. Teste tamanhos de fonte e de widget, rotação, navegação até agosto/2026 (seis linhas), fevereiro/2024 (bissexto) e novembro/2026 (feriados).

Também é possível transferir o APK para o celular e abri-lo em Meus Arquivos, autorizando a instalação dessa origem quando solicitado pelo Android. Para remover: `adb uninstall com.folhinhautil.app`.

## Funcionalidades e organização

```text
app/src/main/
  AndroidManifest.xml                 # READ_CALENDAR e configuração do widget
  java/com/folhinhautil/app/
    MainActivity.kt                   # UI Compose escura, permissões e seleção de agendas
    CalendarViewModel.kt              # Estado da tela e observação do Calendar Provider
    domain/CalendarModels.kt          # Grade mensal, datas e ordenação de eventos
    domain/Holidays.kt                # HolidayProvider e fonte brasileira offline
    data/CalendarRepository.kt        # Calendars + Instances, seleção persistida
    widget/FolhinhaWidget.kt           # Glance responsivo e mudanças de data/fuso
  res/xml/folhinha_widget.xml          # Metadados e periodicidade do widget
app/src/test/                         # Testes JVM de calendário, feriados e eventos
```

- Semana iniciada no domingo, quatro a seis linhas conforme o mês, datas reais e textos em português brasileiro. Janela de navegação: 2003–2099.
- Toque em um dia para ver o nome e a categoria das datas locais da fonte e os compromissos das agendas selecionadas naquele dia.
- Consulta de calendários legíveis e de ocorrências por `CalendarContract.Instances`, incluindo recorrências expandidas pelo Android. Eventos cancelados ou recusados são excluídos.
- Próximos três compromissos em uma janela de 90 dias, incluindo eventos em andamento. Títulos não são registrados em logs. Datas de dia inteiro usam os limites UTC fornecidos pelo Calendar Provider, interpretados como datas locais; fim exclusivo respeitado.
- A primeira autorização seleciona os calendários disponíveis. Depois, a seleção é explícita e persistida apenas por IDs; uma seleção vazia é respeitada. Novos calendários podem ser habilitados em Agendas.
- A única permissão de acesso a dados solicitada ao usuário é `READ_CALENDAR`, ao tocar no botão. Glance/WorkManager acrescentam `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` e uma permissão interna de assinatura para executar e recuperar o trabalho do widget. As permissões transitivas de rede e serviço em primeiro plano foram removidas, pois não são usadas. Não há `WRITE_CALENDAR`, `INTERNET`, OAuth, Firebase, Microsoft Graph ou dados simulados.
- Ao tocar em qualquer parte do widget, o app abre. O widget sempre mostra o mês atual.

## Feriados e fontes oficiais

Fonte embarcada, sem consultas de rede. Os feriados civis recorrentes usam as seguintes leis:

- [Lei 662/1949, redação dada pela Lei 10.607/2002](https://www.planalto.gov.br/ccivil_03/leis/l0662.htm): 1/1, 21/4, 1/5, 7/9, 2/11, 15/11 e 25/12.
- [Lei 6.802/1980](https://www.planalto.gov.br/ccivil_03/leis/l6802.htm): 12/10.
- [Lei 14.759/2023](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/lei/l14759.htm): 20/11, exibido como feriado nacional a partir de 2024. De 2011 a 2023, aparece apenas como data comemorativa pela [Lei 12.519/2011](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2011/lei/l12519.htm).
- [Lei 9.093/1995, art. 2º](https://www.planalto.gov.br/ccivil_03/leis/l9093.htm): Sexta-feira da Paixão depende de legislação municipal. É indicada separadamente, sem presumir a lei de uma cidade. A Páscoa é exibida como data comemorativa. Datas móveis são calculadas pelo algoritmo gregoriano de Meeus/Jones/Butcher.
- [Portaria MGI 11.460/2025](https://www.in.gov.br/web/dou/-/portaria-mgi-n-11.460-de-29-de-dezembro-de-2025-678388627), publicada no DOU de 30/12/2025, Seção 1, p. 59: pontos facultativos federais **somente de 2026**, incluindo Carnaval, Cinzas, Corpus Christi e os dias expressamente previstos. [Divulgação oficial do calendário de 2026](https://www.ifpb.edu.br/servidor/noticias/2026/01/mgi-divulga-lista-de-feriados-e-pontos-facultativos-de-2026). A portaria também classifica Paixão de Cristo no calendário federal; o app informa a ressalva territorial da Lei 9.093/1995.

Marcador vermelho: feriado nacional. Marcador âmbar: ponto facultativo, data comemorativa ou feriado religioso dependente de lei local. No widget, `•` marca feriado nacional e `·` marca outras datas. O detalhe completo está no aplicativo. Pontos facultativos federais não representam automaticamente dispensa de trabalho na iniciativa privada.

Não há consulta estadual/municipal, previsão de portarias futuras nem cadastro de feriados excepcionais/eleitorais. O intervalo anterior a 2003 é excluído para evitar aplicar retroativamente a redação de 2002. O catálogo recorrente é aplicado aos demais anos suportados; mudanças legais futuras exigem atualização do app. `HolidayProvider` permite acrescentar outra fonte sem alterar a UI.

## Atualização e limites

- O sistema solicita atualização do widget a cada hora. O Android e a economia de bateria podem atrasar essa execução; não é uma agenda com alarmes em tempo real.
- O aplicativo atualiza ao retornar ao primeiro plano, ao tocar em Atualizar e ao observar mudanças no Calendar Provider enquanto o processo existe. Também reconsulta a cada minuto enquanto a tela permanece composta; não há serviço permanente ou alarme exato.
- Mudanças de data, hora e fuso solicitam atualização do widget. À noite ou com o processo encerrado, o launcher pode manter a última imagem até a próxima atualização permitida.
- A remoção de permissão é respeitada na próxima consulta. Uma imagem já renderizada pelo launcher pode persistir até essa atualização. Eventos não são gravados em arquivo ou preferências pelo app.
- Não existe lista ilimitada de eventos, criação/edição de compromissos ou seleção de mês no widget neste marco.
- A aparência final do widget, a sincronização Google/Samsung e o comportamento sob economia de bateria precisam ser verificados em um aparelho real. O build e os testes JVM não substituem essa validação.

### Correção do widget no Samsung

O launcher do aparelho de validação informa cerca de 401,33 × 453,87 dp, com fonte 1,15. O antigo limite vertical de 460 dp selecionava o formato compacto. Além disso, calendário e agenda emitiam seus elementos no mesmo pai e excediam o limite de dez filhos diretos do Glance, descartando os elementos finais.

A composição agora usa contêineres explícitos: calendário com sete filhos, semanas com até seis linhas, linhas com sete células, agenda com até cinco filhos e cada compromisso com três. As cores e tamanhos de fonte foram preservados. As variantes são 250×250 (compacta), 250×330 e 360×440 (verticais), 250×540 (vertical alta), 500×250 e 500×360 (horizontais). O tamanho atual do Samsung comporta a variante 360×440.

O widget mede textos com a conversão SP do próprio Android, considerando fonte, largura, quantidade de semanas e quebra de linhas. Exibe o prefixo cronológico de até três compromissos que caiba inteiro no espaço reservado; fontes maiores ou menos altura reduzem a quantidade. Datas e horários podem ocupar até três linhas; os títulos mantêm o limite existente de duas linhas. O estado vazio aparece quando cabe. No calendário compacto, apenas o espaçamento vazio é reduzido quando necessário. Não há alteração nas consultas, seleção de calendários ou permissões.

O mecanismo de atualização de snapshots permanece **inalterado e pendente de investigação separada**: os dados são carregados antes de `provideContent`, e chamadas de `updateAll` durante uma sessão Glance já ativa podem recompor o snapshot anterior. Esta correção trata composição e dimensionamento, sem refatorar a atualização.

Para repetir os testes reais de renderização, sem novas dependências de teste:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
```

O teste compõe RemoteViews reais e verifica presença de calendário, indicadores e eventos, limites de texto, sobreposição e ausência dos diagnósticos de truncamento. Cobre quatro, cinco e seis semanas; zero a três eventos; dia inteiro, horário específico e ocorrências do mesmo ID; todas as variantes e o tamanho real do Samsung; escalas de fonte 1,0, 1,15, 1,3 e 1,5. As fixtures ficam somente na memória dos testes e não são inseridas na agenda.

No aparelho de validação, pode-se acrescentar `-e checkDeviceWidget true` antes do nome do runner para conferir também o evento informado de 26/10/2026, das 16h às 17h, usando a seleção real de calendários. Essa verificação produz somente sucesso/falha, sem títulos, horários ou outros dados pessoais nos logs. O APK de testes é separado e não faz parte do APK do aplicativo.

Validação da correção em 24/10/2026: **11 testes JVM aprovados; lint com 0 erros e 14 avisos preexistentes; build dos dois APKs concluído**. No Samsung Android 16, os dois testes instrumentados passaram: 336 combinações da matriz, incluindo as fontes maiores, e conferência do evento informado em RemoteViews reais nos tamanhos 360×440 e 401,33×453,87 dp. Foram verificados todos os dias, indicadores, textos de agenda, limites dos textos e ausência de sobreposição; nenhum diagnóstico `Truncated Column container` ou `Truncated Row container` ocorreu no processo de teste. A configuração de fonte do aparelho não foi alterada: as demais escalas foram exercitadas em contextos de teste isolados.

O APK corrigido foi instalado no Samsung preservando dados e permissões, e o APK auxiliar de testes foi removido após a execução. O fluxo já existente de atualização ao abrir o aplicativo foi acionado; o processo também não apresentou diagnósticos de truncamento depois disso. A conferência visual final na página do launcher ficou pendente porque o aparelho estava bloqueado. Fontes extremas além das escalas verificadas e títulos muito longos podem exigir um widget maior; o limite de duas linhas dos títulos continua sendo intencional.

SHA-256 do APK corrigido: `48A4D0486AFA63A34E7B388473061A48EB39E5BB49200BC91614BE6F9850083D`.

## Referências técnicas

- [Calendar Provider e ocorrências](https://developer.android.com/identity/providers/calendar-provider).
- [Jetpack Glance e versões](https://developer.android.com/jetpack/androidx/releases/glance).
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

Sem commit ou push nesta entrega.
#   f o l h i n h a - u t i l  
 #   f o l h i n h a - u t i l  
 