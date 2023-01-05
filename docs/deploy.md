## Deploy to Maven Central

The deploy to Maven Central the following steps must be taken:

#### 0. Prepare the Release

The following artifacts need to be ready before the release is performed:

- Release notes
- Version number
- Tag name. For the release script to work correctly the tag must start with the letter v and be followed by a semantic version identifier, e.g. v0.1.0, v0.2.1, etc. The script only removes the v at the beginning, so any version identifier that is a valid Maven Central Version identifier should work.
- All tests must be green

#### 1. Perform the Actual Release

Github does the deployment to Maven Central automatically through a Github Action each time we release a new version of the project and create a tag in the Github Release interface.

#### 2. Close the Maven Central Staging Repository

Once we have created and deployed to Maven Central staging repository, the next step is to close it in the staging repository. To do this, we need to go the [Nexus Repository Manager](https://s01.oss.sonatype.org/#welcome), enter the Staging Repositories section, select the newly created staging repository and select close. This step performs some validations on the pom.xml file. Before publishing, some tests can be performed on the staging repository.

#### 3. Release the Repository

The final step is to release the repository to the Maven Central Repository. This is done in the same part of the Nexus Repository Manager as before by selecting the button Release.