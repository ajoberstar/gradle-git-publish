plugins {
  id("org.ajoberstar.defaults.gradle-plugin")
  id("groovy")

  id("org.ajoberstar.stutter")
}

group = "org.ajoberstar.git-publish"
description = "Gradle plugin for publishing to Git repositories"

mavenCentral {
  developerName.set("Andrew Oberstar")
  developerEmail.set("ajoberstar@gmail.com")
  githubOwner.set("ajoberstar")
  githubRepository.set("gradle-git-publish")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

dependencies {
  compatTestImplementation(gradleTestKit())
  compatTestImplementation("org.spockframework:spock-core:2.3-groovy-3.0")
  compatTestImplementation("org.ajoberstar.grgit:grgit-core:[5.0,6.0[")
}

tasks.named<Jar>("jar") {
  manifest {
    attributes.put("Automatic-Module-Name", "org.ajoberstar.git.publish.gradle")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

stutter {
  val java11 by matrices.creating {
    javaToolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
    gradleVersions {
      compatibleRange("7.0")
    }
  }
  val java17 by matrices.creating {
    javaToolchain {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
    gradleVersions {
      compatibleRange("7.3")
    }
  }
  val java21 by matrices.creating {
    javaToolchain {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
    gradleVersions {
      compatibleRange("8.4")
    }
  }
}

tasks.named("check") {
  dependsOn(tasks.named("compatTest"))
}

gradlePlugin {
  plugins {
    create("plugin") {
      id = "org.ajoberstar.git-publish"
      displayName = "Git Publish Plugin"
      description = "Gradle plugin for publishing to Git repositories"
      implementationClass = "org.ajoberstar.gradle.git.publish.GitPublishPlugin"
    }
  }
}
