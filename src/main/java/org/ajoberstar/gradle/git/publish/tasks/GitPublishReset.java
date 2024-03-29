package org.ajoberstar.gradle.git.publish.tasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.ajoberstar.grgit.Grgit;
import org.ajoberstar.grgit.Ref;
import org.ajoberstar.grgit.gradle.GrgitService;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.util.PatternFilterable;

@UntrackedTask(because = "Git tracks the state")
public class GitPublishReset extends DefaultTask {
  private final Property<GrgitService> grgitService;
  private final Property<String> repoUri;
  private final Property<String> referenceRepoUri;
  private final Property<String> branch;
  private final Property<Integer> fetchDepth;
  private PatternFilterable preserve;
  private final ObjectFactory objectFactory;

  @Inject
  public GitPublishReset(ObjectFactory objectFactory) {
    this.grgitService = objectFactory.property(GrgitService.class);
    this.repoUri = objectFactory.property(String.class);
    this.referenceRepoUri = objectFactory.property(String.class);
    this.branch = objectFactory.property(String.class);
    this.fetchDepth = objectFactory.property(Integer.class);
    this.objectFactory = objectFactory;
  }

  @Internal
  public Property<GrgitService> getGrgitService() {
    return grgitService;
  }

  @Internal
  public Property<String> getReferenceRepoUri() {
    return referenceRepoUri;
  }

  @Input
  public Property<String> getRepoUri() {
    return repoUri;
  }

  @Input
  public Property<String> getBranch() {
    return branch;
  }

  @Input
  @Optional
  public Property<Integer> getFetchDepth() {
    return fetchDepth;
  }

  @Internal
  public PatternFilterable getPreserve() {
    return preserve;
  }

  public void setPreserve(PatternFilterable preserve) {
    this.preserve = preserve;
  }

  @TaskAction
  public void reset() throws IOException {
    var git = grgitService.get().getGrgit();

    if (!isRemoteUriMatch(git, "origin", repoUri.get())) {
      git.getRemote().remove(op -> {
        op.setName("origin");
      });
      git.getRemote().add(op -> {
        op.setName("origin");
        op.setUrl(repoUri.get());
      });
    }

    if (referenceRepoUri.isPresent() && !isRemoteUriMatch(git, "reference", referenceRepoUri.get())) {
      git.getRemote().remove(op -> {
        op.setName("reference");
      });

      git.getRemote().add(op -> {
        op.setName("reference");
        op.setUrl(referenceRepoUri.get());
      });
    }

    var pubBranch = getBranch().get();

    if (!pubBranch.equals(git.getBranch().current().getName())) {
      // create a new orphan branch
      git.checkout(op -> {
        op.setBranch(pubBranch);
        op.setOrphan(true);
      });
    }

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
          op.setDepth(getFetchDepth().getOrNull());
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
        op.setDepth(getFetchDepth().getOrNull());
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
    }

    var repoTree = objectFactory.fileTree();
    repoTree.from(git.getRepository().getRootDir());
    var preservedTree = repoTree.matching(getPreserve());
    var unwantedTree = repoTree.minus(preservedTree).getAsFileTree();
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

  private boolean isRemoteUriMatch(Grgit grgit, String remoteName, String remoteUri) {
    try {
      var maybeCurrentRemoteUri = grgit.getRemote().list().stream()
          .filter(remote -> remote.getName().equals(remoteName))
          .map(remote -> remote.getUrl())
          .findAny();

      // need to use the URIish to normalize them and ensure we support all Git compatible URI-ishs (URL
      // is too limiting)
      if (maybeCurrentRemoteUri.isPresent()) {
        return new URIish(remoteUri).equals(new URIish(maybeCurrentRemoteUri.get()));
      } else {
        return false;
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException("Invalid URI.", e);
    }
  }
}
