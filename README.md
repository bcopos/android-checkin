Android Checkin
================

Android Checkin allows you to register a Google account with a device, as if it was done from the device.

Most of the code for the checkin process is from [android-checkin](https://github.com/nviennot/android-checkin). However, the original code no longer works since Google has changed the authentication process to require encrypted password (vs. plaintext). This repository includes a tool for encrypting Google account passwords in the same fashion as GoogleLoginService.

The password encryption method is the reverse-engineered version of `gapps-jb-20130813-signed/system/app/GoogleLoginService/smali/com/google/android/gsf/loginservice/Password_Encrypter.smali` (aka Android 4.3 JellyBean). It has not been tested on Lollipop but seems to also work for KitKat (4.4).

The mimicked device is a MotoX Gen 1. Also added BLE as one of the hardware components for increased compatbility with applications.

GoogleLoginService Password Encryption
-----

Check "Password_Encrypter.java"


Usage
-----

From the command line:
```shell
# Outputs the registered android_id
java -Xbootclasspath/a:lib/httpcore-4.2.2.jar:lib/httpclient-4.2.2.jar:lib/httpmime-4.2.2.jar:lib/httpclient-cache-4.2.2.jar:lib/commons-logging-1.1.1.jar:lib/fluent-hc-4.2.2.jar:lib/protobuf-java-2.4.1.jar:lib/commons-codec-1.6.jar -jar android-checkin-jb.jar <email> <password>
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

If you get an error like `[ERROR] Failed to execute goal com.github.igor-petruk.protobuf:protobuf-maven-plugin:0.4:run (default) on project android-checkin: Unable to find 'protoc' -> [Help 1]`, download protobuf-2.4.1 [source](https://protobuf.googlecode.com/files/protobuf-2.4.1.tar.gz) and build. Make sure the executable `protoc` is in your PATH.

License
-------

Android Checkin is released under the MIT license.
