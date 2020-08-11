## SPISUM DATABOX

## Version

v1.0-beta2

## Prerequisities

This container have to be deployed at least 10 minutes after first deploy of https://github.com/ISFG/alfresco-core.

- Java 11
- Maven 3.6.x
- Docker
- Docker-compose

## How to run application

In this file **credentials.json** set your credentials for one or more databox accounts

Expected format of **credentials.json** is:

```json
[
  {
    "username": "",
    "password": ""
  },
  {
    "username": "",
    "password": ""
  }
]
```

In this file **docker-compose.yml** set the address where you will run the project

```json
ALFRESCO_REPOSITORY_URL=http://hostname.domain:8082
```

```bash
$ git clone https://github.com/ISFG/java-isds.git
$ cd java-isds
$ mvn clean install -DskipTests
$ cd ..
$ git clone https://github.com/ISFG/spisum-databox.git -b master --single-branch spisum-databox
$ cd spisum-databox
$ mvn clean package
$ docker-compose up -d
```
