# Contributing

Thanks for your interest!

## How to contribute

1. Fork the repository
2. Create your branch:
    ```bash
    git checkout -b main/your-feature
    ```
3. Commit changes
4. Push and open a PR

## Guidelines and development handbook

### Naming

* Package name: All little English letters like `utils`
* File name: Same as the class name,big hump naming like `StringUtils`
* Class name: Big hump naming like `StringUtils`
* Method name: Little hump naming like `toString`
* Local variables,dynamic variables name: Little hump naming like `userName`
* Global const name: Big snake naming like `TIME_THRESHOLD`

> Should name meaningful names like `userAge`,NOT like `int1`.

### Coding

* Add meaningful notes where errors are prone.
* Write detailed `JavaDoc` and notes.

### Git

* Write meaningful and formatted commit message.
Format like
```text
<type>: <Description>

<Detailed description ( optional ) >

<Relational information ( optional )>
```
Type:
* feat: New features
* fix: Fixed bugs
* docs: Documents' changes
* style: Format/Coding style
* refactor: Refactor,neither fix bugs nor new features.
* test: About tests
* chore: Chores (builds, dependencies, etc.)