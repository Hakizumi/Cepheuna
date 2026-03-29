# *Hakihive* -- Personal web assistant

![hakihive_icon](assets/images/icon_narrow.png)

## Project introduction
### *Hakihive* is an open source web assistant,you can use browser to talk with AI simply.

---

## What can *Hakihive* do?
* **Automatically** call tools
* Talk to AI in the **cloud**.Even if your computer is not with you, you can still talk to Hakihive and operate your computer through the web.

### Webpage screenshot 👇
![webpage](assets/images/webpage.png)
![memory_webpage](assets/images/memory_webpage.png)

Able to call tools:
![tool_calling_webpage](assets/images/tool_calling_webpage.png)

---

## How to use? For developers
### Prepare -- Requires
* `JDK` >= 21

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
```shell
mvn clean package    # First: package
java -jar target/hakihive.jar   # Run the jar
```

#### or

```shell
mvn spring-boot:run
```

### Third -- Talk with **Hakihive**
Open your browser ( Any one is ok ) and access http://localhost:11622/
If you have seen the page,that means **Hakihive** is running healthy.
If not,check the program is running, the url you enter is correct.

---

## How to use? For users
### Prepare -- Requires
* `Hakihive one-click deployment CLI program` ( Named `Hakihive-system-platform.zip/.tar.gz` ) ( Enter [Hakihive-Releases](https://github.com/Hakizumi/hakihive/releases) to download )

### First -- Run the `Hakihive one-click deployment CLI program`
Move the `Hakihive one-click deployment CLI program` to a proper path and unzip it,
you will see an `executable program` named `Hakihive.exe`/,double click it.
Then the `Hakihive one-click deployment CLI program` will auto install and deploy all requirements.
After the `Hakihive one-click deployment CLI program` prints `Deployed successfully.`,that means `Hakihive` deployed successfully.

### Second -- Configure your openai-api-url and api-key
At the same-folder of the `Hakihive one-click deployment CLI program`,
enter the path `config/application.yml`.

In the `application.yml`,configure
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxxx  # Enter your api-key,if it is in environment or running in docker,use ${OPENAI_API_KEY} instead
      base-url: https://api.openai.com   # Api url,you can replace it with your own transit station
```

### Third -- Run the Hakihive program
At the same-folder of the `Hakihive one-click deployment CLI program`,
run the start shell ( In Windows,that is `start.bat`,in Mac or Linux,that is `start.sh` ).

### Forth -- Talk with **Hakihive**
Open your browser ( Any one is ok ) and access http://localhost:11622/ ( or http://localhost:11622/chat )
If you have seen the page,that means **Hakihive** is running healthy.
If not,check the program is running, the url you enter is correct.

---

## About **Hakihive**
* Github: https://github.com/Hakizumi/hakihive
* Github-Releases: https://github.com/Hakizumi/hakihive/releases
* Developer: `Hakizumi`
* Contributors: None :(

---

#### LICENSE: [LICENSE](LICENSE)
#### CONTRIBUTING: [CONTRIBUTING](CONTRIBUTING.md)
#### SECURITY: [SECURITY](SECURITY.md)