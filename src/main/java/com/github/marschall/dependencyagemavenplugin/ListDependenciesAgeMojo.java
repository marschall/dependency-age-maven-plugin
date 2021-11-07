package com.github.marschall.dependencyagemavenplugin;

import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.sonatype.plexus.build.incremental.BuildContext;

@Mojo(
        name = "list-dependencies-age",
        threadSafe = true,
        defaultPhase = VERIFY,
        requiresDependencyResolution = RUNTIME
      )
public class ListDependenciesAgeMojo extends AbstractMojo {

  @Component
  private ProjectDependenciesResolver resolver;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repositorySession;

  @Component
  private BuildContext buildContext;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!this.buildContext.hasDelta("pom.xml")) {
      return;
    }

    List<Dependency> dependencies = this.resolveDependencies();
    for (Dependency dependency : dependencies) {
      FileTime creationTime = this.determineAge(dependency);
      if (creationTime != null) {
        LocalDate creationDate = creationTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        this.getLog().info("The creation date of: " + dependency.getArtifact() + " is: " + creationDate);
      }
    }

  }

  private FileTime determineAge(Dependency dependency) throws MojoExecutionException {

    Artifact artifact = dependency.getArtifact();
    if (artifact.getExtension().equals("jar")) {
      try (JarFile jarFile = new JarFile(artifact.getFile())) {
        JarEntry manifest = (JarEntry) jarFile.getEntry("META-INF/MANIFEST.MF");
        if (manifest != null) {
          return manifest.getCreationTime();
        }
      } catch (IOException e) {
        throw new MojoExecutionException("could not open jar of: " + artifact, e);
      }
    }
    return null;
  }

  private List<Dependency> resolveDependencies() throws MojoExecutionException {
    DependencyResolutionRequest request = this.newDependencyResolutionRequest();

    DependencyResolutionResult result;
    try {
      result = this.resolver.resolve(request);
    } catch (DependencyResolutionException e) {
      throw new MojoExecutionException("could not resolve dependencies", e);
    }

    return result.getDependencies();
  }

  private DependencyResolutionRequest newDependencyResolutionRequest() {
    DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
    request.setMavenProject(this.project);
    request.setRepositorySession(this.repositorySession);
    return request;
  }

}
