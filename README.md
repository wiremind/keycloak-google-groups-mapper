# Keycloak Google Groups Identity Provider Mapper

This is a Keycloak extension that fetches Google Groups membership information from a user that authenticates through Google. 

## Usage

This identity provider mapper requires the following config in `keycloak.conf`:

    spi-identity-provider-mapper-google-groups-idp-mapper-service-account-user=<<email-address-of-user-to-impersonate>>

Additionally, it uses the _default_ authentication mechanism of the Google client libraries, which essentially means that you want to set an environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the location of the JSON file you can download for a configured service client in the Google Developer console.

## Building

    mvn clean verify

The build writes `target/keycloak-google-groups-mapper-<version>-jar-with-dependencies.jar`.
Without a local Java 21, build in a container:

    docker run --rm -v "$PWD":/src -w /src maven:3-eclipse-temurin-21 mvn clean verify

Each pull request also uploads this JAR as a workflow artifact.

## Running locally

* Get a keycloak zip release, unpack it.
* Configure `keycloak.conf`, see *Usage*
* Run with `GOOGLE_APPLICATION_CREDENTIALS=<<path-to-json-credentials-file>> DEBUG=true DEBUG_SUSPEND=n ./kc.sh start-dev`

And then check out http://localhost:8080/. You can login with admin/admin.

## Releasing

[release-please](https://github.com/googleapis/release-please) makes the releases from the
[Conventional Commits](https://www.conventionalcommits.org/) on `main`:

1. A merge to `main` with a `feat:` or `fix:` commit opens or updates a release pull request.
   This pull request sets the next version in `pom.xml` and adds the changes to `CHANGELOG.md`.
2. A merge of the release pull request creates the tag `<version>` and the GitHub release.
3. The `Release` workflow then builds the JAR from the tag and attaches two assets to the release:
   `keycloak-google-groups-mapper-<version>-jar-with-dependencies.jar` and its `.sha256` file.

`feat:` increments the minor version and `fix:` increments the patch version.
Before 1.0.0, a breaking change (`feat!:`) also increments the minor version.
`chore:`, `ci:`, `docs:` and `test:` commits do not cause a release.

Renovate keeps the Maven dependencies, the Maven plugins and the GitHub Actions up to date.
Its pull requests use `fix(deps):` for the dependencies, so each merge causes a release.
