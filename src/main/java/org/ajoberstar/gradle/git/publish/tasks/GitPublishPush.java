package org.ajoberstar.gradle.git.publish.tasks;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;

@UntrackedTask(because = "Git tracks the state")
public abstract class GitPublishPush extends DefaultTask {
  @OutputDirectory
  public abstract DirectoryProperty getRepoDir();

  @Input
  public abstract Property<String> getBranch();

  @Inject
  protected abstract ExecOperations getExecOperations();

  @TaskAction
  public void push() {
    var pubBranch = getBranch().get();
    var output = new ByteArrayOutputStream();
    getExecOperations().exec(spec -> {
      var refSpec = String.format("refs/heads/%s:refs/heads/%s", pubBranch, pubBranch);
      spec.commandLine("git", "push", "--porcelain", "--set-upstream", "origin", refSpec);
      spec.workingDir(getRepoDir().get());
      spec.setStandardOutput(output);
    });

    var result = output.toString(StandardCharsets.UTF_8);
    this.setDidWork(!result.contains("[up to date]"));
  }
}
