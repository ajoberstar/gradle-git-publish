package org.ajoberstar.gradle.git.publish;


import org.ajoberstar.gradle.git.publish.tasks.DirectoryPropertyFactory;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

public class GitPublishExtension {
  private final DirectoryProperty repoDir;
  private final Property<String> repoUri;
  private final Property<String> branch;
  private final Property<String> commitMessage;
  private final CopySpec contents;
  private final PatternFilterable preserve;

  public GitPublishExtension(Project project) {
    this.repoDir = DirectoryPropertyFactory.create(project);
    this.repoUri = project.getObjects().property(String.class);
    this.branch = project.getObjects().property(String.class);
    this.commitMessage = project.getObjects().property(String.class);

    this.contents = project.copySpec();
    this.preserve = new PatternSet();
    this.preserve.include(".git");
  }

  public DirectoryProperty getRepoDir() {
    return repoDir;
  }

  public Property<String> getRepoUri() {
    return repoUri;
  }

  public Property<String> getBranch() {
    return branch;
  }

  public Property<String> getCommitMessage() {
    return commitMessage;
  }

  public CopySpec getContents() {
    return contents;
  }

  public void contents(Action<? super CopySpec> action) {
    action.execute(contents);
  }

  public PatternFilterable getPreserve() {
    return preserve;
  }

  public void preserve(Action<? super PatternFilterable> action) {
    action.execute(preserve);
  }
}
