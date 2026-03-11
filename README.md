# *Cepheuna* ( aka *天神座* in Chinese ) -- Personal voice to voice assistant

## Project introduction
### *Cepheuna* is an open source voice assistant,you can use browser to talk with AI simply.

## What can *Cepheuna* do?
* **Real-time** conversational experience,you are able to cost less tokens to experient what it's like to be on a real call.
* **Automatically** call tools to increase your productivity by 200%!
* Talk to AI in the **cloud**.Even if your computer is not with you, you can still talk to VoiceAgent and operate your computer through the web.

## How to use? ( For developers )
### Prepare -- Requires
* Java version / JDK >= 21
* Sherpa Onnx Runtime Lib ( see https://github.com/k2-fsa/sherpa-onnx/releases/ )
* K2fsa Models ( see https://github.com/k2-fsa/sherpa-onnx/releases/ )

#### How to download Sherpa Onnx Runtime and K2fsa Models?
##### 1.Go to https://github.com/k2-fsa/sherpa-onnx/releases/ and download the latest suitable sherpa native lib for your computer ( sherpa-onnx-native-lib-xxx-xxx-vxxx.jar and sherpa-onnx-vxxx.jar)

##### 2.Configure Sherpa native lib dependencies
```xml
<dependency>
    <groupId>com.k2fsa.sherpa.onnx</groupId>
    <artifactId>sherpa-onnx</artifactId>
    <version>sherpa-version</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/sherpa-onnx-v1.12.28.jar</systemPath>
</dependency>

<dependency>
    <groupId>com.k2fsa.sherpa.onnx</groupId>
    <artifactId>sherpa-onnx-native-lib-win</artifactId>
    <version>sherpa-version</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/sherpa-onnx-native-lib-win-x64-v1.12.28.jar</systemPath>
</dependency>
```
##### 3.Go to https://github.com/k2-fsa/sherpa-onnx/releases/ and download the latest suitable sherpa model for your computer ( sherpa-onnx-vxxx-xxx-xxx.tar.bz2 )

##### 4.Unzip the tar.bz file and copy all files to ./models

##### 5.In application.yml:
```yaml
audio:
  sherpa-tokens: models.tokens.txt  # tokens file
  
  # After unzip,you will see a set of files with similar names
  # Compared with the ordinary model, the int8 model increases the recognition speed, but the accuracy is slightly worse 
  # Use int8 model if you focus more on low latency ( Recommended )
  sherpa-joiner: models.joiner-epoch-99-avg-1.int8.onnx
  sherpa-encoder: models.encoder-epoch-99-avg-1.int8.onnx
  sherpa-decoder: models.decoder-epoch-99-avg-1.int8.onnx
```

### First -- Configure your openai-api-url and api-key

#### In application.yml:
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

## Configurable entries
### In application.yml
```yaml
server:
  port: 8080   # server port,default 8080 ( spring )

spring:
  application:
    name: Cepheuna

  ai:
    openai:
      api-key: your-api-key
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
  version: 1.0.0
  
  audio:
    # see org.sempiria.cepheuna.config.AudioProperties
    sample-rate: 16000
    bit-depth: 16
    channels: 1
    frame-ms: 20

    sherpa-tokens: models/tokens.txt
    sherpa-joiner: models/joiner-epoch-99-avg-1.int8.onnx
    sherpa-encoder: models/encoder-epoch-99-avg-1.int8.onnx
    sherpa-decoder: models/decoder-epoch-99-avg-1.int8.onnx

    asr-threads: 1
    vad-rms-threshold: 0.015
    silence-trigger-frame: 18
    speech-trigger-frame: 6

    buffer-high-ms: 2500
    buffer-low-ms: 800
    buffer-start-ms: 1200
    
  tokenizer:
    # see org.sempiria.cepheuna.config.StreamingTokenizerProperties
    cut: ",.;?!~，。；？！\n"
    soft-length: 48
    aggressive-soft-length: 24
    hard-length: 160
    min-emit-chars: 12
    aggressive-min-emit-chars: 8

logging:
  level:
    root: info
    org.sempiria.cepheuna: debug  # optional,for logging

```

## About **Cepheuna**
* Github: https://github.com/Hakizumi/Cepheuna
* Contributors: Hakizumi
* Version: 1.0.0