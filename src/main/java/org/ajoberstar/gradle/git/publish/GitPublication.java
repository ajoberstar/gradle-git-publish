package org.ajoberstar.gradle.git.publish;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

public class GitPublication implements Named {
  private final String name;
  private final DirectoryProperty repoDir;
  private final Property<String> repoUri;
  private final Property<String> referenceRepoUri;
  private final Property<String> branch;
  private final Property<Integer> fetchDepth;
  private final Property<String> commitMessage;
  private final Property<Boolean> sign;
  private final CopySpec contents;
  private final PatternFilterable preserve;
  private final Property<String> username;
  private final Property<String> password;

  public GitPublication(String name, Project project, ObjectFactory objectFactory) {
    this.name = name;
    this.repoDir = objectFactory.directoryProperty();
    this.repoUri = objectFactory.property(String.class);
    this.referenceRepoUri = objectFactory.property(String.class);
    this.branch = objectFactory.property(String.class);
    this.fetchDepth = objectFactory.property(Integer.class);
    this.commitMessage = objectFactory.property(String.class);
    this.sign = objectFactory.property(Boolean.class);

    this.contents = project.copySpec();
    this.preserve = new PatternSet();
    this.preserve.include(".git/**/*");

    this.username = objectFactory.property(String.class);
    this.password = objectFactory.property(String.class);
  }

  @Override
  public String getName() {
    return name;
  }

  public DirectoryProperty getRepoDir() {
    return repoDir;
  }

  public Property<String> getRepoUri() {
    return repoUri;
  }

  public Property<String> getReferenceRepoUri() {
    return referenceRepoUri;
  }

  public Property<String> getBranch() {
    return branch;
  }

  public Property<Integer> getFetchDepth() {
    return fetchDepth;
  }

  public Property<String> getCommitMessage() {
    return commitMessage;
  }

  public Property<Boolean> getSign() {
    return sign;
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

  public Property<String> getUsername() {
    return username;
  }

  public Property<String> getPassword() {
    return password;
  }
}
