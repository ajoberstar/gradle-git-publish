package org.ajoberstar.gradle.git.publish;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.util.PatternFilterable;

public class GitPublishExtension {
  private final NamedDomainObjectContainer<GitPublication> publications;

  @Inject
  public GitPublishExtension(Project project, ObjectFactory objectFactory) {
    this.publications = objectFactory.domainObjectContainer(GitPublication.class, name -> new GitPublication(name, project, objectFactory));
  }

  public NamedDomainObjectContainer<GitPublication> getPublications() {
    return publications;
  }

  public void publications(Action<? super NamedDomainObjectContainer<? super GitPublication>> action) {
    action.execute(publications);
  }

  public DirectoryProperty getRepoDir() {
    return publications.getByName("main").getRepoDir();
  }

  public Property<String> getRepoUri() {
    return publications.getByName("main").getRepoUri();
  }

  public Property<String> getReferenceRepoUri() {
    return publications.getByName("main").getReferenceRepoUri();
  }

  public Property<String> getBranch() {
    return publications.getByName("main").getBranch();
  }

  public Property<String> getCommitMessage() {
    return publications.getByName("main").getCommitMessage();
  }

  public Property<Boolean> getSign() {
    return publications.getByName("main").getSign();
  }

  public CopySpec getContents() {
    return publications.getByName("main").getContents();
  }

  public void contents(Action<? super CopySpec> action) {
    publications.getByName("main").contents(action);
  }

  public PatternFilterable getPreserve() {
    return publications.getByName("main").getPreserve();
  }

  public void preserve(Action<? super PatternFilterable> action) {
    publications.getByName("main").preserve(action);
  }
}
