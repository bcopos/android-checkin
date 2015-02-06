Android Checkin
================

Android Checkin allows you to register a Google account with a device, as if it was done from the device.

Most of the code for the checkin process is from [android-checkin](https://github.com/nviennot/android-checkin). However, the original code did not work since Google changed their Authentication process to require encrypted password (vs. plaintext). This repository includes a tool for encrypting Google account passwords in the same fashion as GoogleLoginService.

The password encryption method is the reverse-engineered version of `gapps-jb-20130813-signed/system/app/GoogleLoginService/smali/com/google/android/gsf/loginservice/Password_Encrypter.smali` (aka Android 4.3 JellyBean). It has not been tested on KitKat or Jellybean.

TODO:
- figure out why it needs to manually add deps to classpath

GoogleLoginService Password Encryption
-----

Check "Password_Encrypter.java"


Usage
-----

From the command line:
```shell
# Outputs the registered android_id
java -Xbootclasspath/a:lib/httpcore-4.2.2.jar:lib/httpclient-4.2.2.jar:lib/httpmime-4.2.2.jar:lib/httpclient-cache-4.2.2.jar:lib/commons-logging-1.1.1.jar:lib/fluent-hc-4.2.2.jar:lib/protobuf-java-2.4.1.jar -jar android-checkin-jb.jar <email> <password> <androidId>
```

To obtain androidId via adb
```shell
adb shell settings get secure android_id
```

Building
----

From the command line:
```shell
	mvn clean
	mvn package
```

