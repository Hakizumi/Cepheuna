# *Cepheuna* ( aka *天神座* in Chinese ) -- Personal voice to voice assistant

## Project introduction
### *Cepheuna* is an open source voice assistant,you can use browser to talk with AI simply.

---

## What can *Cepheuna* do?
* **Real-time** conversational experience,you are able to cost less tokens to experient what it's like to be on a real call.
* **Automatically** call tools
* Talk to AI in the **cloud**.Even if your computer is not with you, you can still talk to Cepheuna and operate your computer through the web.

---

## How to use? ( For developers )
### Prepare -- Requires
* JDK >= 21
* Sherpa Onnx Runtime Lib ( see [Sherpa-releases](https://github.com/k2-fsa/sherpa-onnx/releases/) )
* Sherpa Onnx Models ( see [Sherpa-releases](https://github.com/k2-fsa/sherpa-onnx/releases/) )

#### To download and deploy sherpa-onnx models and runtime lib,see [sherpa-onnx-deployment-guild](sherpa-onnx-deployment-guild.md)

### First -- Configure your openai-api-url and api-key

#### In `application.yml`:
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxxx  # Enter your api-key,if it is in environment or running in docker,use ${OPENAI_API_KEY} instead
      base-url: https://api.openai.com   # Api url,you can replace it with your own transit station
```

#### Second -- Add the Sherpa api and Sherpa runtime lib to classpath
In `pom.xml`
```xml
<dependency>
    <groupId>com.k2fsa.sherpa.onnx</groupId>
    <artifactId>sherpa-onnx</artifactId>
    <version>latest-sherpa-version</version>
</dependency>
<dependency>
    <groupId>com.k2fsa.sherpa.onnx</groupId>
    <artifactId>sherpa-onnx-native-lib-xxx</artifactId>
    <version>latest-sherpa-version</version>
</dependency>
```

> The Sherpa onnx api and Sherpa onnx native lib is not published on maven central repository
> So you should download it and install it to your local repository,like
```shell
mvn install:install-file -Dfile=.\sherpa-onnx-vx.x.x.jar -DgroupId=com.k2fsa.sherpa.onnx -DartifactId=sherpa-onnx -Dversion=x.x.x -Dpackaging=jar
mvn install:install-file -Dfile=.\sherpa-onnx-vx.x.x-native-lib-xxx.jar -DgroupId=com.k2fsa.sherpa.onnx -DartifactId=sherpa-onnx-native-lib-xxx -Dversion=x.x.x -Dpackaging=jar
```

### Third -- Compile the source and run the project
```shell
mvn clean package    # First: package
java -jar target/cepheuna.jar   # Run the jar
```

#### or

```shell
mvn spring-boot:run
```

### Forth -- Talk with **Cepheuna**
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

### First -- Download latest version of Cepheuna one-click deployment kit on [Cepheuna-releases](https://github.com/Hakizumi/Cepheuna/releases),and move it to a proper path
> The one-click deployment kit is already integrated stt and tts models,that means you don't need to download the Sherpa models yourself. 

### Second -- Unzip the Cepheuna one-click deployment kit
#### In `./config/application.yml`
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxxx  # Enter your api-key,must configure
      base-url: https://api.openai.com   # Api url,you can replace it with your own transit station
```

#### And you can cover the configuration entries.( see one-click-deployment-kit/config/application.yml to get more information )

### Third -- Run the jar-file
#### If you are `Windows`,double-click the `start-windows.bat`
#### If you are `Mac/Linux aarch64`,double-click the `start-mac-linux-aarch64.sh`
#### If you are `Mac/Linux x64`,double-click the `start-mac-linux-x64.sh`

### Forth -- Talk with **Cepheuna**
#### Open your browser ( Any one is ok ) and access http://localhost:11622/
#### If you have seen the page,that means **Cepheuna** is running healthy.
#### If not,check the program is running, the url you enter is correct,and you do configure port 11622.If you change your port, you should also change the URL you enter.

---

## About **Cepheuna**
* Github: https://github.com/Hakizumi/Cepheuna
* Github-Releases: https://github.com/Hakizumi/Cepheuna/releases
* Developer: `Hakizumi`
* Contributors: None :(
* Current Version: 1.3.1

---

#### LICENSE: [LICENSE](LICENSE)
#### CONTRIBUTING: [CONTRIBUTING](CONTRIBUTING.md)
#### SECURITY: [SECURITY](.github/SECURITY.md)