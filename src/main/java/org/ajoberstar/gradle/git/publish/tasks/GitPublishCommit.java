package org.ajoberstar.gradle.git.publish.tasks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;

@UntrackedTask(because = "Git tracks the state")
public abstract class GitPublishCommit extends DefaultTask {
  @OutputDirectory
  public abstract DirectoryProperty getRepoDir();

  @Input
  public abstract Property<String> getMessage();

  @Input
  @Optional
  public abstract Property<Boolean> getSign();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  public void commit() {
    // add changed files
    getExecOperations().exec(spec -> {
      spec.commandLine("git", "add", "-A");
      spec.workingDir(getRepoDir().get());
      spec.setStandardOutput(OutputStream.nullOutputStream());
    });

    // check for changes to commit
    var status = new ByteArrayOutputStream();
    getExecOperations().exec(spec -> {
      spec.commandLine("git", "status", "--porcelain");
      spec.workingDir(getRepoDir().get());
      spec.setStandardOutput(status);
    });

    if (status.toString(StandardCharsets.UTF_8).isEmpty()) {
      this.setDidWork(false);
      return;
    }

    // commit changes
    getExecOperations().exec(spec -> {
      spec.executable("git");
      spec.args("commit");

      // signing
      if (getSign().isPresent()) {
        spec.args(getSign().get() ? "--gpg-sign" : "--no-gpg-sign");
      }

      // message
      spec.args("--file", "-");
      var msg = getMessage().get().getBytes(StandardCharsets.UTF_8);
      spec.setStandardInput(new ByteArrayInputStream(msg));

      spec.workingDir(getRepoDir().get());

      spec.setStandardOutput(OutputStream.nullOutputStream());
    });

    this.setDidWork(true);
  }
}
