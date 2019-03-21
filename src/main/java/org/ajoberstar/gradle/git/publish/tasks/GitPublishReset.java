package org.ajoberstar.gradle.git.publish.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.ajoberstar.grgit.Grgit;
import org.ajoberstar.grgit.Ref;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;

public class GitPublishReset extends DefaultTask {
  private final Property<Grgit> grgit;
  private final DirectoryProperty repoDirectory;
  private final Property<String> repoUri;
  private final Property<String> branch;
  private PatternFilterable preserve;

  @Inject
  public GitPublishReset(ProjectLayout layout, ObjectFactory objectFactory) {
    this.grgit = objectFactory.property(Grgit.class);
    this.repoDirectory = DirectoryPropertyFactory.create(layout, objectFactory);
    this.repoUri = objectFactory.property(String.class);
    this.branch = objectFactory.property(String.class);

    // always consider this task out of date
    this.getOutputs().upToDateWhen(t -> false);
  }

  @Internal
  public Property<Grgit> getGrgit() {
    return grgit;
  }

  @OutputDirectory
  public DirectoryProperty getRepoDirectory() {
    return repoDirectory;
  }

  @Input
  public Property<String> getRepoUri() {
    return repoUri;
  }

  @Input
  public Property<String> getBranch() {
    return branch;
  }

  @Internal
  public PatternFilterable getPreserve() {
    return preserve;
  }

  public void setPreserve(PatternFilterable preserve) {
    this.preserve = preserve;
  }

  @TaskAction
  public void reset() {
    Grgit git = findExistingRepo().orElseGet(() -> freshRepo());
    grgit.set(git);

    String pubBranch = getBranch().get();

    Map<Ref, String> remoteBranches = git.lsremote(op -> {
      op.setRemote("origin");
      op.setHeads(true);
    });

    boolean remoteBranchExists = remoteBranches.keySet().stream()
        .anyMatch(ref -> ref.getFullName().equals("refs/heads/" + pubBranch));

    if (remoteBranchExists) {
      // fetch only the existing pages branch
      git.fetch(op -> {
        op.setRefSpecs(Arrays.asList(String.format("+refs/heads/%s:refs/remotes/origin/%s", pubBranch, pubBranch)));
        op.setTagMode("none");
      });

      // make sure local branch exists
      if (!git.getBranch().list().stream().anyMatch(branch -> branch.getName().equals(pubBranch))) {
        git.getBranch().add(op -> {
          op.setName(pubBranch);
          op.setStartPoint("origin/" + pubBranch);
        });
      }

      // get to the state the remote has
      git.clean(op -> {
        op.setDirectories(true);
        op.setIgnore(false);
      });
      git.checkout(op -> op.setBranch(pubBranch));
      git.reset(op -> {
        op.setCommit("origin/" + pubBranch);
        op.setMode("hard");
      });
    } else {
      // create a new orphan branch
      git.checkout(op -> {
        op.setBranch(pubBranch);
        op.setOrphan(true);
      });
    }

    // clean up unwanted files
    FileTree repoTree = getProject().fileTree(git.getRepository().getRootDir());
    FileTree preservedTree = repoTree.matching(getPreserve());
    FileTree unwantedTree = repoTree.minus(preservedTree).getAsFileTree();
    unwantedTree.visit(new FileVisitor() {
      @Override
      public void visitDir(FileVisitDetails fileVisitDetails) {
        // do nothing
      }

      @Override
      public void visitFile(FileVisitDetails fileVisitDetails) {
        try {
          Files.delete(fileVisitDetails.getFile().toPath());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    });

    // stage the removals, relying on dirs not being tracked by git
    git.add(op -> {
      op.setPatterns(Stream.of(".").collect(Collectors.toSet()));
      op.setUpdate(true);
    });
  }

  private Optional<Grgit> findExistingRepo() {
    try {
      return Optional.of(Grgit.open(op -> op.setDir(repoDirectory.get().getAsFile())))
          .filter(repo -> {
            try {
              String originUri = getOriginUri(repo);
              // need to use the URIish to normalize them and ensure we support all Git compatible URI-ishs (URL
              // is too limiting)
              boolean valid = new URIish(repoUri.get()).equals(new URIish(originUri)) && branch.get().equals(repo.getBranch().current().getName());
              if (!valid) {
                repo.close();
              }
              return valid;
            } catch (URISyntaxException e) {
              throw new RuntimeException("Invalid URI.", e);
            }
          });
    } catch (Exception e) {
      // missing, invalid, or corrupt repo
      getProject().getLogger().debug("Failed to find existing Git publish repository.", e);
      return Optional.empty();
    }
  }

  private Grgit freshRepo() {
    getProject().delete(repoDirectory.get().getAsFile());

    Grgit repo = Grgit.init(op -> {
      op.setDir(repoDirectory.get().getAsFile());
    });
    repo.getRemote().add(op -> {
      op.setName("origin");
      op.setUrl(repoUri.get());
    });
    return repo;
  }

  private String getOriginUri(Grgit grgit) {
    return grgit.getRemote().list().stream()
        .filter(remote -> remote.getName().equals("origin"))
        .map(remote -> remote.getUrl())
        .findAny()
        .orElse(null);
  }
}
