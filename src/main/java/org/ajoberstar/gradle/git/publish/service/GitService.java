package org.ajoberstar.gradle.git.publish.service;

import org.ajoberstar.grgit.Grgit;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URISyntaxException;
import java.util.Optional;

public abstract class GitService implements BuildService<GitService.Params>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(GitService.class);

  public interface Params extends BuildServiceParameters {

    DirectoryProperty getRepoDirectory();

    Property<String> getRepoUri();

    Property<String> getReferenceRepoUri();

    Property<String> getBranch();

  }

  private Grgit grgit;

  public GitService() {
      this.grgit = findExistingRepo().orElseGet(this::freshRepo);
  }

  private Optional<Grgit> findExistingRepo() {
    try {
      return Optional.of(Grgit.open(op -> op.setDir(getParameters().getRepoDirectory().get().getAsFile())))
               .filter(repo -> {
                 boolean valid = isRemoteUriMatch(repo, "origin", getParameters().getRepoUri().get())
                   && (!getParameters().getReferenceRepoUri().isPresent() || isRemoteUriMatch(repo, "reference", getParameters().getReferenceRepoUri().get()))
                   && getParameters().getBranch().get().equals(repo.getBranch().current().getName());
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

  private Grgit freshRepo() {
    getFs().delete(spec -> spec.delete(getParameters().getRepoDirectory().get().getAsFile()));

    Grgit repo = Grgit.init(op -> {
      op.setDir(getParameters().getRepoDirectory().get().getAsFile());
    });
    repo.getRemote().add(op -> {
      op.setName("origin");
      op.setUrl(getParameters().getRepoUri().get());
    });
    if (getParameters().getReferenceRepoUri().isPresent()) {
      repo.getRemote().add(op -> {
        op.setName("reference");
        op.setUrl(getParameters().getReferenceRepoUri().get());
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

  public Grgit getGrGit() {
    return grgit;
  }

  @Inject
  protected abstract FileSystemOperations getFs();

  @Override
  public void close() {
    LOG.info("Closing Git publish repo: {}", getParameters().getRepoDirectory().get());
    grgit.close();
  }
}
