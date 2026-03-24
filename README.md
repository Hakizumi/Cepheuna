# *Cepheuna* -- Personal web assistant

## Project introduction
### *Cepheuna* is an open source web assistant,you can use browser to talk with AI simply.

---

## What can *Cepheuna* do?
* **Automatically** call tools
* Talk to AI in the **cloud**.Even if your computer is not with you, you can still talk to Cepheuna and operate your computer through the web.

---

## How to use?
### Prepare -- Requires
* JDK >= 21
* Sherpa Onnx Runtime Lib ( see [Sherpa-releases](https://github.com/k2-fsa/sherpa-onnx/releases/) )
* Sherpa Onnx Models ( see [Sherpa-releases](https://github.com/k2-fsa/sherpa-onnx/releases/) )

#### To download and deploy sherpa-onnx models and runtime lib,see [sherpa-onnx-deployment-guild](sherpa-onnx-deployment-guide.md)

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
#### Open your browser ( Any one is ok ) and access http://localhost:11622/
#### If you have seen the page,that means **Cepheuna** is running healthy.
#### If not,check the program is running, the url you enter is correct.

---

## About **Cepheuna**
* Github: https://github.com/Hakizumi/Cepheuna
* Github-Releases: https://github.com/Hakizumi/Cepheuna/releases
* Developer: `Hakizumi`
* Contributors: None :(

---

#### LICENSE: [LICENSE](LICENSE)
#### CONTRIBUTING: [CONTRIBUTING](CONTRIBUTING.md)
#### SECURITY: [SECURITY](SECURITY.md)