#!/bin/sh
mvn clean package -U -Dmaven.test.skip=true

cp hiveV1-backend/target/hiveV1-backend-1.18.6.jar .

zip -r hiveV1.zip  ./hiveV1-backend-1.18.6.jar ./hiveV1Driver   ./plugin.json
