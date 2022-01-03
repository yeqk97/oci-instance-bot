# OCI Instance Bot

Simple spring boot application that tries to launch a VM.Standard.A1.Flex
instance until an instance is launched or there is another instance with the same
shape.

## Build

```
./mvnw clean package
docker build -t yeqk/oci-instance-bot .
```

## Run

1. Create a folder with these files:
   1. application.yaml ([example file](https://github.com/yeqk97/oci-instance-bot/blob/main/src/main/resources/application.yaml)).
   2. config ([example file](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#Example_Configuration)).
   3. private key file obtained
      following [this guide](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#two).
2. Update application.yaml "api-config-path" property to "/app/config/config"
3. Update config file key_path to "/app/config/XXX" where "XXX" is the private
   key file name.
4. From inside the folder run:
```
docker run -it -v ${PWD}:/app/config yeqk/oci-instance-bot
```