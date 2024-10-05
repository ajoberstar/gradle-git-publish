package org.ajoberstar.gradle.git.publish.tasks;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.process.ExecOperations;

@UntrackedTask(because = "Git tracks the state")
public abstract class GitPublishReset extends DefaultTask {
  private PatternFilterable preserve;

  @OutputDirectory
  public abstract DirectoryProperty getRepoDir();

  @Internal
  public abstract Property<String> getReferenceRepoUri();

  @Input
  public abstract Property<String> getRepoUri();

  @Input
  public abstract Property<String> getBranch();

  @Input
  @Optional
  public abstract Property<Integer> getFetchDepth();

  @Internal
  public PatternFilterable getPreserve() {
    return preserve;
  }

  public void setPreserve(PatternFilterable preserve) {
    this.preserve = preserve;
  }

  @Inject
  protected abstract ObjectFactory getObjectFactory();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  public void reset() throws IOException {
    var repoDir = getRepoDir().get().getAsFile();
    var pubBranch = getBranch().get();

    // initialize git repo
    if (!new File(repoDir, ".git").exists()) {
      getExecOperations().exec(spec -> {
        spec.commandLine("git", "init");
        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
      });
    }

    // set origin
    try {
      getExecOperations().exec(spec -> {
        spec.commandLine("git", "remote", "add", "origin", getRepoUri().get());
        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
        spec.setErrorOutput(OutputStream.nullOutputStream());
      });
    } catch (Exception e) {
      getExecOperations().exec(spec -> {
        spec.commandLine("git", "remote", "set-url", "origin", getRepoUri().get());
        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
      });
    }

    // set reference
    if (getReferenceRepoUri().isPresent()) {
      try {
        getExecOperations().exec(spec -> {
          spec.commandLine("git", "remote", "add", "reference", getReferenceRepoUri().get());
          spec.workingDir(repoDir);
          spec.setStandardOutput(OutputStream.nullOutputStream());
          spec.setErrorOutput(OutputStream.nullOutputStream());
        });
      } catch (Exception e) {
        getExecOperations().exec(spec -> {
          spec.commandLine("git", "remote", "set-url", "reference", getReferenceRepoUri().get());
          spec.workingDir(repoDir);
          spec.setStandardOutput(OutputStream.nullOutputStream());
        });
      }

      // check reference for branch
      boolean referenceHasBranch;
      try {
        getExecOperations().exec(spec -> {
          spec.commandLine("git", "ls-remote", "--exit-code", "reference", pubBranch);
          spec.workingDir(repoDir);
          spec.setStandardOutput(OutputStream.nullOutputStream());
        });
        referenceHasBranch = true;
      } catch (Exception e) {
        referenceHasBranch = false;
      }

      if (referenceHasBranch) {
        // get local branch reset to remote state
        getExecOperations().exec(spec -> {
          var refSpec = String.format("+refs/heads/%s:refs/remotes/reference/%s", pubBranch, pubBranch);

          spec.executable("git");
          spec.args("fetch");
          if (getFetchDepth().isPresent()) {
            spec.args("--depth", getFetchDepth().get());
          }
          spec.args("reference", refSpec);

          spec.workingDir(repoDir);
          spec.setStandardOutput(OutputStream.nullOutputStream());
        });
      }
    }

    // check origin for branch
    boolean hasBranch;
    try {
      getExecOperations().exec(spec -> {
        spec.commandLine("git", "ls-remote", "--exit-code", "origin", pubBranch);
        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
      });
      hasBranch = true;
    } catch (Exception e) {
      hasBranch = false;
    }

    if (hasBranch) {
      // get local branch reset to remote state
      getExecOperations().exec(spec -> {
        var refSpec = String.format("+refs/heads/%s:refs/remotes/origin/%s", pubBranch, pubBranch);

        spec.executable("git");
        spec.args("fetch");
        if (getFetchDepth().isPresent()) {
          spec.args("--depth", getFetchDepth().get());
        }
        spec.args("origin", refSpec);

        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
      });

      getExecOperations().exec(spec -> {
        spec.commandLine("git", "switch", "--force-create", pubBranch, String.format("origin/%s", pubBranch));
        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
        spec.setErrorOutput(OutputStream.nullOutputStream());
      });
    } else {
      // start with a fresh branch
      getExecOperations().exec(spec -> {
        spec.commandLine("git", "switch", "--orphan", pubBranch);
        spec.workingDir(repoDir);
        spec.setStandardOutput(OutputStream.nullOutputStream());
        spec.setErrorOutput(OutputStream.nullOutputStream());
      });
    }

    // clean repository
    getExecOperations().exec(spec -> {
      spec.commandLine("git", "clean", "-fdx");
      spec.workingDir(repoDir);
      spec.setStandardOutput(OutputStream.nullOutputStream());
    });

    // remove all files not marked in the preserve
    var repoTree = getObjectFactory().fileTree();
    repoTree.from(repoDir);
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
    getExecOperations().exec(spec -> {
      spec.commandLine("git", "add", "-A");
      spec.workingDir(repoDir);
      spec.setStandardOutput(OutputStream.nullOutputStream());
    });
  }
}
