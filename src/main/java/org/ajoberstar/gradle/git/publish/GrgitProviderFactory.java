package org.ajoberstar.gradle.git.publish;

import org.ajoberstar.gradle.git.publish.service.GitService;
import org.ajoberstar.grgit.Grgit;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Optional;

class GrgitProviderFactory {

  private static final Logger LOG = LoggerFactory.getLogger(GrgitProviderFactory.class);

  private static final GradleVersion FIRST_VERSION_WITH_BUILD_SERIVCE_SUPPORT = GradleVersion.version("6.1");

  static Provider<Grgit> createGrgit(Project project, GitPublishExtension extension) {
    if (GradleVersion.current().compareTo(FIRST_VERSION_WITH_BUILD_SERIVCE_SUPPORT) >= 0) {
      return GrgitProviderFactory.fromBuildService(project, extension);
    } else {
      return GrgitProviderFactory.legacyGrgit(project, extension);
    }
  }

  private static Provider<Grgit> fromBuildService(Project project, GitPublishExtension extension) {
    return project.getGradle().getSharedServices().registerIfAbsent("gitService", GitService.class, spec -> {
      spec.parameters(params -> {
        params.getRepoDirectory().set(extension.getRepoDir());
        params.getRepoUri().set(extension.getRepoUri());
        params.getReferenceRepoUri().set(extension.getReferenceRepoUri());
        params.getBranch().set(extension.getBranch());
      });
      spec.getMaxParallelUsages().set(1);
    }).map(s -> s.getGrGit());
  }

  private static Provider<Grgit> legacyGrgit(Project project, GitPublishExtension extension) {
    Provider<Grgit> grgit = project.provider(() -> findExistingRepo(extension).orElseGet(() -> {
      project.delete(extension.getRepoDir());
      return freshRepo(extension);
    }));

    // always close the repo at the end of the build
    project.getGradle().buildFinished(result -> {
      LOG.info("Closing Git publish repo: {}", extension.getRepoDir().get());
      if (grgit.isPresent()) {
        grgit.get().close();
      }
    });

    return grgit;
  }

  private static Optional<Grgit> findExistingRepo(GitPublishExtension extension) {
    try {
      return Optional.of(Grgit.open(op -> op.setDir(extension.getRepoDir().get().getAsFile())))
            .filter(repo -> {
              boolean valid = isRemoteUriMatch(repo, "origin", extension.getRepoUri().get())
                    && (!extension.getReferenceRepoUri().isPresent() || isRemoteUriMatch(repo, "reference", extension.getReferenceRepoUri().get()))
                    && extension.getBranch().get().equals(repo.getBranch().current().getName());
              if (!valid) {
                repo.close();
              }
              return valid;
            });
    } catch (Exception e) {
      // missing, invalid, or corrupt repo
      LOG.debug("Failed to find existing Git publish repository.", e);
      return Optional.empty();
    }
  }

  private static Grgit freshRepo(GitPublishExtension extension) {
    Grgit repo = Grgit.init(op -> {
      op.setDir(extension.getRepoDir().get().getAsFile());
    });
    repo.getRemote().add(op -> {
      op.setName("origin");
      op.setUrl(extension.getRepoUri().get());
    });
    if (extension.getReferenceRepoUri().isPresent()) {
      repo.getRemote().add(op -> {
        op.setName("reference");
        op.setUrl(extension.getReferenceRepoUri().get());
      });
    }
    return repo;
  }

  private static boolean isRemoteUriMatch(Grgit grgit, String remoteName, String remoteUri) {
    try {
      String currentRemoteUri = grgit.getRemote().list().stream()
            .filter(remote -> remote.getName().equals(remoteName))
            .map(remote -> remote.getUrl())
            .findAny()
            .orElse(null);

      // need to use the URIish to normalize them and ensure we support all Git compatible URI-ishs (URL
      // is too limiting)
      return new URIish(remoteUri).equals(new URIish(currentRemoteUri));
    } catch (URISyntaxException e) {
      throw new RuntimeException("Invalid URI.", e);
    }
  }
}
