# tvhome-custom

Uma tela inicial personalizada para Android TV, escrita como **referência para o seu agente de código**. Copie um prompt, cole no Claude Code, no Codex ou num agente parecido, e ele cria uma tela inicial como esta para você.

[English](README.md)

## O que é

Uma tela só, em Java puro, sem Gradle e sem bibliotecas (cerca de 84 KB):

- Seus apps como blocos focáveis, organizados pelo controle: segure **OK** para mover, substituir ou remover, e adicione qualquer app instalado.
- Relógio, data e um cartão de clima (Open-Meteo, sem chave).
- Entradas HDMI e atalhos numa fileira embaixo.
- Uma fileira opcional de filmes e séries em alta (chave grátis do TMDB).
- Um visual de noite e outro de manhã, fundo desenhado uma vez, feito para ser leve em TVs lentas.

## Use com o seu agente

1. Ative o modo desenvolvedor na TV: **Configurações → Preferências do dispositivo → Sobre**, aperte **Build** 7 vezes. Depois ative a **Depuração USB** (ou a depuração por rede) em **Opções do desenvolvedor**. A TV e o computador precisam estar no mesmo Wi-Fi.
2. Abra o seu agente numa pasta vazia.
3. Abra o [`PROMPT.pt-BR.md`](PROMPT.pt-BR.md) ([`PROMPT.md`](PROMPT.md) em inglês), copie tudo abaixo da linha e troque `<URL_DO_REPO>` pelo endereço deste repositório.
4. Cole. O agente lê este repositório, faz algumas perguntas (TV, apps, idioma, cidade do clima, fileira de títulos em alta), cria a sua versão e instala na sua TV, perguntando antes de cada mudança.

> [!NOTE]
> Isto é o que funcionou para nós, não é aconselhamento profissional. Use por sua conta e risco. Você é responsável pela sua TV.

## O que tem no repositório

| Caminho | O que é |
| --- | --- |
| `PROMPT.md`, `PROMPT.pt-BR.md` | O prompt para colar no agente |
| `src/com/tvhome/example/MainActivity.java` | A tela inicial: blocos, foco, menu de edição, rodapé, configurações |
| `Weather.java`, `Trending.java`, `Net.java` | Clima, fileira de títulos em alta do TMDB, GET HTTPS mínimo |
| `Look.java`, `Landscape.java`, `LineIcon.java` | Cores de noite/manhã, fundo desenhado, ícones vetoriais |
| `assets/` | A fonte incluída |
| `AndroidManifest.xml` | Declaração do launcher HOME e visibilidade de apps (`queries`) |
| `apps.txt` | Apps padrão, um nome de pacote por linha. Copie para `apps.local.txt` (fora do git) para a sua lista |
| `build.sh`, `build.ps1` | Compilam o APK (macOS/Linux e Windows) |
| `install.sh`, `undo.sh` | Instalam e definem como tela inicial padrão, e desfazem |
| `ci/smoke-test.sh`, `.github/workflows/build.yml` | Teste em emulador e CI |

## Compile e rode o exemplo você mesmo

Você precisa de Java 17, do Android SDK (plataforma 34, build-tools 34.0.0) e do `adb`. No Mac:

```sh
brew install openjdk@17 android-platform-tools
brew install --cask android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

No Windows, instale o `adb` pelas [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) do Google e rode os scripts no Git Bash, ou use o `build.ps1`.

```sh
sh build.sh                      # gera out/tvhome.apk
sh install.sh <IP_DA_TV>         # instala e define como tela inicial padrão
sh install.sh <IP_DA_TV> "com.netflix.ninja,com.google.android.youtube.tv"   # ou escolha os apps
sh undo.sh <IP_DA_TV>            # devolve a TV ao normal
```

A maioria das TVs continua abrindo a tela inicial original, então o `install.sh` oferece desativá-la. Nada é desinstalado.

A chave de assinatura é criada na primeira compilação em `~/.tvhome` (`key.jks` e `key.pass`), fora do repositório. Faça backup: as atualizações precisam ser assinadas com a mesma chave.

Para a fileira de títulos em alta, pegue uma chave de API grátis em [themoviedb.org](https://www.themoviedb.org/settings/api) e envie para a TV:

```sh
adb shell am start -n com.tvhome.example/.MainActivity --es tmdb <SUA_CHAVE>
```

## Créditos

Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.

Licença MIT. Veja o [LICENSE](LICENSE).
