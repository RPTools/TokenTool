Token Tool
==========

This project builds the Token Tool. It uses the 1.4 libraries.

Building
========

This project depends on the launch4j gradle plugin found 
[here](https://github.com/netvl/launch4gradle). You will need to build and 
install that plugin using a **native gradle installation**, so that is available
in your local maven repository for use, before you will be able to complete this
build.

Releasing
=========

Run the following gradle targets:

```bash
./gradlew build release startScripts
```

To create the output zips in **build/**

To run these you can:

1. extract the zip files into an **install/** directory
2. create a **bin/** folder in the resulting **install/** directory
3. copy **build/scripts/tokentool[.bat]** into the newly created **install/bin/** directory
4. make sure **tokentool[.bat]** is executable
5. move the **tokentool<garbagehere>.jar** to the **install/lib** directory
5. run **tokentool[.bat]** (change directory or double click, assuming your OS has
support) for these files.

