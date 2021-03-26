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
import org.gradle.api.file.*;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.util.GradleVersion;

public class GitPublishReset extends DefaultTask {
  private final Property<Grgit> grgit;
  private final DirectoryProperty repoDirectory;
  private final Property<String> repoUri;
  private final Property<String> referenceRepoUri;
  private final Property<String> branch;
  private ObjectFactory objectFactory;
  private PatternFilterable preserve;

  @Inject
  public GitPublishReset(ObjectFactory objectFactory) {
    this.grgit = objectFactory.property(Grgit.class);
    this.repoDirectory = objectFactory.directoryProperty();
    this.repoUri = objectFactory.property(String.class);
    this.referenceRepoUri = objectFactory.property(String.class);
    this.branch = objectFactory.property(String.class);
    this.objectFactory = objectFactory;

    // always consider this task out of date
    this.getOutputs().upToDateWhen(t -> false);
  }

  @Internal
  public Property<Grgit> getGrgit() {
    return grgit;
  }

  @Internal
  public Property<String> getReferenceRepoUri() {
    return referenceRepoUri;
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
    Grgit git = grgit.get();

    String pubBranch = getBranch().get();

    if (referenceRepoUri.isPresent()) {
      Map<Ref, String> referenceBranches = git.lsremote(op -> {
        op.setRemote("reference");
        op.setHeads(true);
      });

      boolean referenceBranchExists = referenceBranches.keySet().stream()
          .anyMatch(ref -> ref.getFullName().equals("refs/heads/" + pubBranch));

      if (referenceBranchExists) {
        getLogger().info("Fetching from reference repo: " + referenceRepoUri.get());
        git.fetch(op -> {
          op.setRefSpecs(Arrays.asList(String.format("+refs/heads/%s:refs/remotes/reference/%s", pubBranch, pubBranch)));
          op.setTagMode("none");
        });
      }
    }

    Map<Ref, String> remoteBranches = git.lsremote(op -> {
      op.setRemote("origin");
      op.setHeads(true);
    });

    boolean remoteBranchExists = remoteBranches.keySet().stream()
        .anyMatch(ref -> ref.getFullName().equals("refs/heads/" + pubBranch));

    if (remoteBranchExists) {
      // fetch only the existing pages branch
      git.fetch(op -> {
        getLogger().info("Fetching from remote repo: " + repoUri.get());
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
    FileTree repoTree = getRepoTree(git);
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

  private FileTree getRepoTree(Grgit git) {
    if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) >= 0) {
      return objectFactory.fileTree().from(git.getRepository().getRootDir());
    } else {
      return getProject().fileTree(git.getRepository().getRootDir());
    }
  }
}
