# Prompt: criar uma tela inicial personalizada para Android TV

Copie tudo abaixo desta linha e cole no seu agente de código (Claude Code, Codex ou parecido), com o agente aberto numa pasta vazia do seu computador.

---

Quero uma tela inicial (launcher) personalizada para a minha Android TV, feita do mesmo jeito que o projeto de exemplo em <URL_DO_REPO>. Use esse repositório como **referência para aprender e adaptar**, não como algo para copiar às cegas. Responda no meu idioma.

## Passo 0: aprenda com o exemplo

1. Clone <URL_DO_REPO> numa pasta temporária fora do meu projeto. Leia o `README.md` e depois todos os arquivos de `src/` (comece por `MainActivity.java`), `AndroidManifest.xml`, `build.sh`, `install.sh`, `undo.sh` e `ci/smoke-test.sh`.
2. Antes de escrever código, resuma para mim em poucas linhas o que aprendeu e como pretende adaptar.
3. Pergunte tudo que precisar numa única mensagem:
   - Marca e modelo da minha TV, e a versão do Android se eu souber.
   - Quais apps quero na tela inicial, em ordem. Se eu não souber o nome do pacote, descubra na TV com `adb shell pm list packages`.
   - Meu idioma e a cidade do clima (senão ela vem do meu IP público).
   - Se quero uma fileira de "filmes e séries em alta" (precisa de uma chave grátis da API do TMDB, em themoviedb.org) ou não.
   - O que quero diferente do exemplo (cores, layout, sem clima etc.).

## O que o app precisa fazer

Um launcher de Android TV de uma tela só, em Java puro, sem Gradle e sem bibliotecas.

**Manifest**
- Uma activity com intent filter `android.intent.action.MAIN` + `android.intent.category.HOME` + `android.intent.category.DEFAULT`, `launchMode="singleTask"`, paisagem, fora dos apps recentes.
- `uses-feature` para `android.software.leanback` e para touchscreen, ambos com `required="false"`.
- `minSdk 21`, `targetSdk 34`. Permissões: só `INTERNET` e `ACCESS_NETWORK_STATE`.
- Um bloco `<queries>` com as intents `MAIN` + `LEANBACK_LAUNCHER` e `MAIN` + `LAUNCHER`; sem ele, o Android 11+ esconde os outros apps do launcher.

**Tela inicial**
- Desenhada em 1920x1080 e escalada para a tela real por um único auxiliar `px()`.
- Uma grade de blocos de apps. Cada bloco é um cartão focável com o ícone e o nome do app, e um anel de foco visível que levanta um pouco o cartão. A navegação usa só as setas e o OK do controle. Sem toque.
- Segurar OK, ou apertar Menu, num bloco abre um menu pequeno: mover (as setas deslizam o bloco, OK confirma), substituir, remover da tela inicial. Remover nunca desinstala nada.
- Um botão "adicionar app" que lista todos os apps abríveis que ainda não estão na tela.
- A lista de apps fica salva em `SharedPreferences`. Se não houver nada salvo, ela vem de uma lista embutida no app na hora da compilação a partir de um arquivo de texto (`apps.txt`, um nome de pacote por linha), completada com outros apps de TV instalados, ignorando os launchers originais e as ferramentas do sistema. Apps que não estão instalados são ignorados.
- Uma fileira de botões em formato de pílula: as entradas HDMI da TV (encontradas com `TvInputManager`, abertas pelo app de TV ao vivo), Configurações e "adicionar app".
- Reage a apps instalados ou removidos.
- Relógio e data, atualizados a cada minuto.
- Cartão de clima (opcional): temperatura atual e os próximos três dias. Cidade e coordenadas pelo IP público (`ipapi.co`, com `ipwho.is` de reserva), previsão pelo Open-Meteo (sem chave). O JSON bruto fica em cache em `SharedPreferences` e é interpretado ao carregar. Se a rede falhar, continua mostrando o cache.
- Fileira de títulos em alta (opcional): TMDB `trending/{movie|tv}/day`, pôsteres baixados uma vez para a pasta de cache e limpos para ficar só os que estão na tela, com uma etiqueta de nota. A chave do TMDB chega pelo comando de instalação, nunca pelo código-fonte.
- Estilo visual: um visual "noite" das 19h às 6h e um visual "manhã" no resto do dia, cartões de vidro translúcido, fundo desenhado uma vez num bitmap RGB_565 e depois só copiado, ícones de contorno desenhados como caminhos vetoriais em código e uma fonte incluída em `assets/`.
- Precisa ser leve em TVs lentas: sem imagens no layout, sem animação além da elevação do foco, sem bibliotecas, uma única thread de fundo para a rede, e toda chamada de rede falha em silêncio (retorna `null`).

**Configuração pelo computador, sem editar na TV**
- `adb shell am start -n <pacote>/.MainActivity --es apps "pkg1,pkg2" --es tmdb <chave> --es stamp <unico>`. `--es apps auto` volta para a lista embutida. O `stamp` faz o app aplicar os extras uma vez só, porque o Android pode reiniciar a tela inicial com a mesma intent antiga e isso desfaria as mudanças feitas na TV.

## Compilação e instalação (scripts simples)

- `build.sh` (macOS/Linux) e `build.ps1` (Windows): geram o `Apps.java` a partir da lista de apps, depois `aapt2 link` (só manifest e assets), `javac -source 1.8 -target 1.8` com o jar da plataforma Android e o `core-lambda-stubs.jar`, `d8`, adicionam o `classes.dex` ao APK, `zipalign`, `apksigner`. Precisa de JDK 17 e do Android SDK plataforma 34 e build-tools 34.0.0.
- A chave de assinatura é criada na primeira compilação **fora do repositório** (`~/.tvhome`), com senha aleatória. As atualizações precisam ser assinadas com a mesma chave. Nunca faça commit de keystore nem de senha.
- `install.sh <IP_DA_TV> ["pkg1,pkg2"]`: `adb connect`, instala, define o app como tela inicial padrão (`cmd role add-role-holder --user 0 android.app.role.HOME <pacote>` no Android 10+, e `cmd package set-home-activity`). Se o launcher da própria TV ainda tomar a frente, **pergunte a mim primeiro**, depois desative-o com `pm disable-user` (nunca desinstale) e registre o que foi desativado em `/data/local/tmp/` na TV.
- `undo.sh <IP_DA_TV>`: reativa tudo que o instalador desativou, desinstala o app e volta para o Início.

## Regras para você, o agente

1. Pergunte antes de cada mudança na TV e antes de instalar qualquer coisa no meu computador.
2. Nunca desinstale apps do sistema. Desative com `pm disable-user` e mantenha uma lista de como desfazer cada mudança.
3. Nunca desative Google Play, teclado, Bluetooth nem as entradas HDMI.
4. Nunca coloque chaves de API, senhas, endereços IP, minha cidade ou keystores no repositório nem em arquivos que vão para commit. Use um `.gitignore` para a keystore, a saída da compilação e qualquer lista local de apps.
5. Não copie o nome do pacote, o nome nem o visual do exemplo. Dê ao meu app nome de pacote, nome e visual próprios.
6. Se algo falhar, mostre o erro de verdade e o que vai tentar em seguida. Não esconda falhas.

## Confira o seu trabalho

- Compile e instale na minha TV. Aperte Início e tire uma captura de tela com `adb exec-out screencap -p`, e olhe a imagem.
- Confira que o Início abre o app, que as setas movem o foco, que OK abre um app e que segurar OK abre o menu de edição.
- Crie verificações no estilo do `ci/smoke-test.sh` para o que você construiu e execute.
- Termine entregando: o código, os scripts de compilação e instalação, um README curto do meu app explicando como compilar, instalar e desfazer, e os comandos exatos que você rodou na minha TV para eu poder reverter.
