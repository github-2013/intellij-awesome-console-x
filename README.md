Awesome Console
===============

This is a plugin for JetBrains IDEs that finally makes links in your console and terminal at least 200% more awesome!

With this plugin all files and links in the console and terminal will be highlighted and can be clicked. Source code files will be opened in the IDE, other links with the default viewer/browser for this type.
Now you just need to configure your favorite Logger to include the file name (and optionally a line number) and you can instantly jump to that file that throws this stupid error.

![](https://github.com/anthraxx/intellij-awesome-console/blob/master/data/screenshot.png)

Links are integrated for the following types:
- source
- file
- url

**(This plugin requires your IDE to run on Java 8 or higher.)**


## Installation
Download the latest release jar

Install plugin from disk

Restart the IDE after installation.

## Note

Gradle should run with Java 11.

Get the Source Code

```
git clone https://github.com/anyesu/intellij-awesome-console
```
Build from the Command Line

You can locate the generated JAR file in the build/libs directory.

## Documentation

| Document | Description |
|----------|-------------|
| [USAGES.md](USAGES.md) | Usage Scenarios & Examples |
| [CODEBUDDY.md](CODEBUDDY.md) | Project Architecture |
| [GUIDE.md](GUIDE.md) | Development Configuration & FAQ |