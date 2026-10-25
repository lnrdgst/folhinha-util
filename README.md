# Folhinha Útil

Calendário Android com feriados brasileiros, integração com as agendas do dispositivo e widget para a tela inicial.

Um aplicativo nativo de calendário pessoal, com tema escuro e foco em simplicidade.

## Funcionalidades

- Calendário mensal em português brasileiro, com navegação entre meses e retorno à data atual.
- Semana começando no domingo, com sábados e domingos em vermelho e destaque para hoje.
- Grade ajustada a meses de quatro, cinco ou seis semanas; cada legenda aparece somente quando há indicadores da respectiva cor no mês.
- Feriados nacionais offline, com indicadores e consulta ao nome e à categoria de cada data.
- Oito fases da Lua offline na grade e nos detalhes de cada data, com ícones vetoriais em convenção do hemisfério sul.
- Pontos facultativos, datas comemorativas e feriados religiosos locais identificados separadamente.
- Leitura dos calendários disponíveis no Android, incluindo Google Agenda quando sincronizado.
- Seleção das agendas exibidas, com suporte a ocorrências recorrentes e eventos de dia inteiro.
- Próximas datas importantes: compromissos das agendas em ordem cronológica e atualização manual pelo ícone de refresh.
- Widget responsivo com navegação mensal independente por instância e retorno ao mês atual pelo título.
- Calendário e feriados nacionais lado a lado no widget, com datas facultativas/comemorativas e até dois eventos pessoais abaixo, conforme o espaço.
- Eventos pessoais locais: cadastro, edição, exclusão com confirmação e repetição anual opcional.
- Contagem regressiva em dias corridos, com indicação de eventos que acontecem hoje.

## Capturas de tela

Seção reservada para capturas reais do aplicativo e do widget.
As imagens ainda não estão versionadas no repositório.
Ao adicioná-las, use dados de demonstração e remova informações pessoais.

## Tecnologias

- Kotlin e `java.time` para datas e horários.
- Jetpack Compose para a interface do aplicativo.
- Jetpack Glance para o widget Android.
- Android `CalendarContract` para leitura das agendas do dispositivo.
- Preferences DataStore com representação JSON versionada para eventos pessoais.
- Gradle Kotlin DSL para configuração e build.

### Requisitos de build

| Componente | Configuração do projeto |
| --- | --- |
| Android mínimo | Android 8.0, API 26 |
| SDK de compilação e alvo | API 36 |
| JDK | 17 ou 21; bytecode Java 17 |
| Gradle Wrapper | 8.14 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin e compilador Compose | 2.2.20 |
| Compose BOM | 2025.06.01 |
| Jetpack Glance | 1.1.1 |

As versões estão definidas em [build.gradle.kts](build.gradle.kts),
[app/build.gradle.kts](app/build.gradle.kts) e no [Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties).

## Executando o projeto

Execute os comandos abaixo no PowerShell, na raiz do repositório.

### 1. Preparar o ambiente

1. Instale o JDK 17 ou 21 e configure `JAVA_HOME` para o JDK escolhido.
2. No Android Studio, instale o SDK Platform 36, Build-Tools e Platform-Tools pelo SDK Manager.
3. Abra o projeto e configure o Gradle JDK e o caminho do SDK. O Android Studio pode gerar `local.properties`, ignorado pelo Git.
4. Disponibilize `java` e `adb` no `PATH` da sessão. O Gradle é executado pelo wrapper do projeto.

A primeira compilação precisa de internet para baixar as ferramentas e dependências.
O aplicativo não precisa de conexão para calcular o calendário, os feriados locais e as fases da Lua.

### 2. Executar testes e lint

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

Relatórios gerados em:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

A cobertura, os testes instrumentados e as evidências estão em [docs/VALIDACAO.md](docs/VALIDACAO.md).

### 3. Gerar o APK de debug

```powershell
.\gradlew.bat assembleDebug
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Instalar pelo ADB

Ative a depuração USB no celular, conecte-o ao computador e autorize a conexão.

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

O aparelho deve aparecer como `device`. A opção `-r` atualiza a instalação preservando os dados.
Abra o Folhinha Útil e selecione as agendas em **Agendas**, após autorizar a leitura.
Para adicionar o widget, use o painel de widgets da tela inicial do Android e ajuste seu tamanho.

## Widget e eventos pessoais

As setas do widget percorrem os meses de 2003 a 2099; tocar no título retorna ao mês atual.
Cada widget guarda seu próprio mês, sem alterar a seleção da tela principal.
A coluna lateral, sem título redundante, mostra os três primeiros feriados nacionais desde o primeiro dia do mês exibido, incluindo datas já passadas nesse mês. A busca continua pelos meses/anos seguintes, em ordem e sem duplicação. O catálogo termina em 2099; dezembro de 2099 contém somente o Natal e não é extrapolado.
Em tamanhos insuficientes, um aviso “N feriados · amplie” substitui a lista; sua descrição acessível contém todas as datas e nomes, sem alterar a seleção.
As contagens dos eventos pessoais sempre partem de hoje, independentemente da navegação.

Em **Eventos pessoais**, informe título e data e, se desejar, ative **Repetir anualmente**.
Eventos anuais de 29/02 ocorrem em 28/02 nos anos não bissextos; após a data, passam à próxima ocorrência.
Eventos não anuais passados permanecem no cadastro, mas saem da lista de próximos eventos.
Os registros ficam somente no aparelho, sem sincronização na nuvem; desinstalar o app apaga esses dados.

O widget prioriza o calendário completo, depois os feriados nacionais e os eventos que couberem.
A seção **Próximos eventos pessoais** mostra somente registros locais; os compromissos Google/Android continuam na tela principal.
O grupo de datas amarelas, sem título redundante, seleciona as duas últimas datas de cada grupo no mês exibido (até dois pontos facultativos e duas outras datas), em ordem crescente.
As categorias e os indicadores amarelos são preservados; essas datas ficam separadas dos feriados nacionais por uma linha sutil. Quando cabe sem perder eventos pessoais, o grupo amarelo ocupa a coluna direita; nos demais tamanhos, fica abaixo do calendário.
O número de itens nas seções inferiores diminui quando o tamanho ou a fonte não permitem mostrar todos com legibilidade.

## Fases da Lua

Cada dia da grade tem um indicador vetorial, além do marcador de feriado. Selecionar uma data mostra sua fase mesmo sem compromissos ou feriados. As quatro fases principais exibem horário local aproximado. As fases intermediárias ocupam os dias entre esses eventos. Não é exibido percentual de iluminação.

O cálculo usa Meeus, capítulo 49, com correções periódicas e planetárias e conversão TT/UT por ΔT. Não usa rede ou persistência. Os ícones são esquemáticos em convenção do hemisfério sul; não reproduzem a rotação aparente no céu. Algoritmo, fontes, precisão e limitações estão em [docs/LUA.md](docs/LUA.md). O widget permanece sem ícones lunares.

## Permissões e funcionamento

- O acesso às agendas usa `READ_CALENDAR` e depende de autorização explícita do usuário.
- Os calendários precisam estar disponíveis no Android. Para Google Agenda, a conta e a sincronização devem estar configuradas no dispositivo.
- A aplicação somente lê os calendários Android: não solicita `WRITE_CALENDAR` nem modifica as agendas de origem. Eventos pessoais são registros locais separados e independem dessa permissão.
- Não há backend, autenticação própria ou login Google dentro do aplicativo.
- Glance/WorkManager usam permissões técnicas para executar e recuperar o widget, incluindo `WAKE_LOCK` e `RECEIVE_BOOT_COMPLETED`.

O app reconsulta os eventos ao entrar em primeiro plano, pelo refresh e por mudanças observadas no Calendar Provider.
Enquanto está em primeiro plano, também faz uma verificação por minuto.
O widget solicita atualização a cada hora, em mudanças de data, hora ou fuso e após alterações nos eventos pessoais.
O Android e a economia de bateria podem adiar essas atualizações; a sincronização das contas é responsabilidade do sistema.

## Limitações atuais

- A navegação mensal abrange 2003 a 2099. O catálogo local não é uma reconstrução completa de todas as alterações legais históricas.
- Os pontos facultativos federais cadastrados correspondem somente a 2026; portarias futuras exigem atualização do catálogo.
- Não há consulta de feriados estaduais ou municipais, cadastro de feriados personalizados ou cobertura de feriados excepcionais e eleitorais.
- A Sexta-feira da Paixão é indicada como feriado religioso dependente de lei municipal; a Páscoa aparece como data comemorativa.
- Na tela principal, **Próximas datas importantes** considera uma janela de 90 dias e exibe até três compromissos das agendas.
- No widget, o número de itens depende do tamanho e da fonte; títulos têm até duas linhas. Tamanhos compactos podem mostrar somente o calendário.
- Não há edição de compromissos das agendas de origem, notificações ou alarmes. A atualização do widget não é em tempo real.

Detalhes da atualização durante sessões Glance ativas estão em [docs/VALIDACAO.md](docs/VALIDACAO.md).

## Evoluções futuras

Possibilidades ainda não implementadas, sem cronograma definido:

- Feriados estaduais e municipais.
- Seleção de município e futura identificação opcional da localização, mediante autorização.
- Feriados personalizados.
- Eventual integração corporativa Microsoft, condicionada às APIs, permissões oficiais e políticas da organização.

## Referências

### Regras de feriados

- [Lei nº 662/1949, com redação da Lei nº 10.607/2002](https://www.planalto.gov.br/ccivil_03/leis/l0662.htm)
- [Lei nº 6.802/1980 — Nossa Senhora Aparecida](https://www.planalto.gov.br/ccivil_03/leis/l6802.htm)
- [Lei nº 12.519/2011 — data comemorativa da Consciência Negra](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2011/lei/l12519.htm)
- [Lei nº 14.759/2023 — feriado nacional em 20 de novembro](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/lei/l14759.htm)
- [Lei nº 9.093/1995 — feriados e legislação municipal](https://www.planalto.gov.br/ccivil_03/leis/l9093.htm)
- [Portaria MGI nº 11.460/2025 — divulgação oficial do calendário federal de 2026](https://www.ifpb.edu.br/servidor/noticias/2026/01/mgi-divulga-lista-de-feriados-e-pontos-facultativos-de-2026)

### Documentação Android

- [Calendar Provider e ocorrências de eventos](https://developer.android.com/identity/providers/calendar-provider)
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
- [Versões do Jetpack Glance](https://developer.android.com/jetpack/androidx/releases/glance)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
