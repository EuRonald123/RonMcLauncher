# Ron MC Launcher

Um launcher (iniciador) pessoal de Minecraft Java, feito para rodar desde as versões antigas até as mais modernas de forma simples, comunicando direto com os servidores originais do jogo. 

Projeto para fins educacionais

## Funcionalidadesuncionalidade

- **Gerenciamento de Perfis**: Salva e carrega suas informações e jogadores, assim você não precisa digitar tudo de novo sempre que for jogar.
- **Java Automático**: O jogo precisa de versões diferentes do Java dependendo da versão do Minecraft (ex: as mais novas precisam do Java 21). O launcher baixa a versão certa sozinho, isolando tudo para não bagunçar o seu PC.
- **Download Inteligente**: Ele baixa todos os arquivos importantes, blocos, sons e bibliotecas que o jogo precisa diretamente da Mojang.
- **Identificação do Sistema**: Consegue saber sozinho se você está no Windows, Linux ou Mac para configurar as coisas certas.
- **Organização e Execução**: Ele agrupa todos os arquivos necessários (o famoso "Classpath") e inicia o jogo em segundo plano.

## Pré-requisitos

Para trabalhar no código ou compilar você vai precisar de:
- **Java 21** ou mais atual instalado.
- **Maven** (ferramenta para ajudar a transformar o código no programa final).

## Como compilar e executar

1. Clone e entre na pasta:
   ```bash
   cd ron-mclauncher
   ```

2. Compile o projeto utilizando Maven:
   ```bash
   mvn clean package
   ```

3. Execute:
   ```bash
   java -jar target/ron-mclauncher-1.0-SNAPSHOT.jar
   ```


- `MinecraftLauncher.java` - É por aqui que o programa começa a rodar.
- **Gerenciadores (`manager/`)**:
  - `DownloadManager.java` - Responsável por baixar os arquivos e coisas do jogo.
  - `ProfileManager.java` - Cuida dos seus dados e dos perfis de jogadores offline.
  - `JavaManager.java` - Garante que você tenha o Java certo pra jogar.
- **Sistema Operacional (`os/`)**:
  - `OSdetection.java` - Descobre qual sistema você usa (Windows, Linux, Mac).
- **Iniciadores do Jogo (`classpath/`)**:
  - `ClassPathBuilder.java` - Organiza todo aquele amontoado de arquivos que o Minecraft precisa pra iniciar.
  - `GameRunner.java` - O motor final que dá o comando para o seu jogo iniciar pra valer.