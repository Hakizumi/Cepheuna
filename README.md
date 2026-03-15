# *Cepheuna* ( aka *天神座* in Chinese ) -- Personal voice to voice assistant

## Project introduction
### *Cepheuna* is an open source voice assistant,you can use browser to talk with AI simply.

---

## What can *Cepheuna* do?
* **Real-time** conversational experience,you are able to cost less tokens to experient what it's like to be on a real call.
* **Automatically** call tools to increase your productivity by 200%!
* Talk to AI in the **cloud**.Even if your computer is not with you, you can still talk to Cepheuna and operate your computer through the web.

---

## How to use? ( For developers )
### Prepare -- Requires
* Java version / JDK >= 21
* Sherpa Onnx Runtime Lib ( see [Sherpa-releases](https://github.com/k2-fsa/sherpa-onnx/releases/) )
* K2fsa Models ( see [Sherpa-releases](https://github.com/k2-fsa/sherpa-onnx/releases/) )

#### To deploy sherpa-onnx models and runtime lib,see [sherpa-onnx-deployment-guild](sherpa-onnx-deployment-guild.md)

### First -- Configure your openai-api-url and api-key

#### In `application.yml`:
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxxx  # Enter your api-key,if it is in environment or running in docker,use ${OPENAI_API_KEY} instead
      base-url: https://api.openai.com   # Api url,you can replace it with your own transit station
```

### Second -- Compile the source and run the project
```xml
<!-- Add the lombok processor -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

```shell
mvn clean package    # First: package
java -jar target/voiceagent.jar   # Run the jar
```

#### or

```shell
mvn spring-boot:run
```

### Third -- Talk with **Cepheuna**
#### Open your browser ( Any one is ok ) and access http://localhost:11622/ ( or http://localhost:11622/index.html , both are ok)
#### If you have seen the page,that means **Cepheuna** is running healthy.
#### If not,check the program is running, the url you enter is correct,and you do configure port 11622.If you change your port, you should also change the URL you enter.

---

## How to use? ( For users )
### Prepare -- Requires
* JRE >= 21

#### To download `JRE` ( `Java runtime environment` ),see [ORACLE-JDK-releases](https://www.oracle.com/java/technologies/downloads/)
#### Run in shell:
```shell
java --version
```
#### If java version is printed successfully,that means JRE is deployed successfully.

### First -- Download latest version of Cepheuna one-click deployment package on [Cepheuna-releases](https://github.com/Hakizumi/Cepheuna/releases),and move it to a proper path

### Second -- Unzip the Cepheuna one-click deployment package
#### In `./config/application.yml`
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxxx  # Enter your api-key,must configure
      base-url: https://api.openai.com   # Api url,you can replace it with your own transit station
```

#### And you can cover the configuration entries.( see [Configurable entries](README.md#configurable-entries) )

### Third -- Run the jar-file
#### If you are `Windows`,double-click the `start-windows.bat`
#### If you are `Mac/Linux`,double-click the `start-mac-linux.sh`

### Forth -- Talk with **Cepheuna**
#### Open your browser ( Any one is ok ) and access http://localhost:11622/
#### If you have seen the page,that means **Cepheuna** is running healthy.
#### If not,check the program is running, the url you enter is correct,and you do configure port 11622.If you change your port, you should also change the URL you enter.

---

## Configurable entries
### In `application.yml`
```yaml
server:
  port: 11622   # server port,default 11622 ( spring )

spring:
  ai:
    openai:
      api-key: your-api-key   # Must configure
      base-url: https://api.openai.com   # openai api url

      chat:
        options:
          model: gpt-4o-mini    # chat model

      audio:
        speech:
          options:
            model: gpt-4o-mini-tts    # speech model
            voice: alloy
            response-format: pcm      # format
            speed: 1.08

cepheuna:
  audio:
    # see org.sempiria.cepheuna.config.AudioProperties
    sample-rate: 16000
    bit-depth: 16
    channels: 1
    frame-ms: 20

    asr-threads: 1
    vad-rms-threshold: 0.015
    silence-trigger-frame: 18
    speech-trigger-frame: 6

    buffer-high-ms: 2500
    buffer-low-ms: 800
    buffer-start-ms: 1200

  models:
    stt:
    token-file-path: models/stt/tokens.txt
    joiner-file-path: models/stt/joiner-epoch-99-avg-1.int8.onnx
    encoder-file-path: models/stt/encoder-epoch-99-avg-1.int8.onnx
    decoder-file-path: models/stt/decoder-epoch-99-avg-1.int8.onnx

    tts:
      # If not configure the 'tts' branch,or any following entries ( except 'language' ) is null
      # Cepheuna will choose online text to speech mode ( Call online openai model )
      token-file-path: models/tts/tokens.txt
      voices-file-path: models/tts/voices.bin
      model-file-path: models/tts/model.onnx
      data-path: models/tts/espeak-ng-data
      lexicon-file-path: models/tts/lexicon-us-en.txt
      dict-path: models/tts/dict
      language: en_us  # optional

  tokenizer:
    # see org.sempiria.cepheuna.config.StreamingTokenizerProperties
    cut: ",.;?!~，。；？！\n"
    soft-length: 48
    aggressive-soft-length: 24
    hard-length: 160
    min-emit-chars: 12
    aggressive-min-emit-chars: 8
    
  tools:
    # config about ai-agent-tools
    io-tools:
      # see org.sempiria.cepheuna.tools.IOTools
      whitelist-paths: /    # All
      blacklist-paths: C:/  # blacklist paths ( model cannot access )

      # If the whitelist and blacklist have duplicated entries, an IllegalArgumentException will be reported
      
logging:
  level:
    root: info
    org.sempiria.cepheuna: debug  # optional,for logging
```

---

## About **Cepheuna**
* Github: https://github.com/Hakizumi/Cepheuna
* Github-Releases: https://github.com/Hakizumi/Cepheuna/releases
* Developer: `Hakizumi`
* Contributors: None :(
* Version: 1.2.1