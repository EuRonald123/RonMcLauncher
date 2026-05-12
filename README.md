# Ron MC Launcher

Launcher pessoal de minecraft java adaptado para rodar versões modernas através de requisições às APIs originais. 

Projeto para fins educacionais

## Funcionalidades

- **Gerenciador de Perfis**: Salva e carrega perfis de usuários localmente para agilizar o login.
- **Isolamento de JRE**: Baixa e isola versões do Java necessárias para rodar versões específicas do Minecraft (ex: Java 21 para Minecraft 1.20+ e outras distros via Adoptium API).
- **Download Inteligente**: Resolve e baixa bibliotecas (`.jar`), assets (índices e objetos) e pacotes nativos diretamente dos servidores da Mojang.
- **Execução Otimizada/Silenciosa**: Monta o Classpath complexo exigido pelo jogo nativamente na sua máquina e o executa em segundo plano.

## Pré-requisitos

- **Java 21** ou superior local.
- **Maven** (usado para gerenciamento e build).

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

## Arquitetura Base
- `MinecraftLauncher.java` - Entrada via CLI.
- `DownloadManager.java` - Handlers de APIs da Mojang.
- `ProfileManager.java` - Gestão do UUID e contas offline.
- `JavaManager.java` - Resolve Runtimes (JREs/JDKs) para cada versão de jogo.
- `GameRunner.java` - Executável base, gerador de classpaths complexos.