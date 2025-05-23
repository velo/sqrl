package com.datasqrl;

import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * validates the schemas based on comprehensiveTest.sqrl script and snapshots the deployment assets
 */
public class GraphQLValidationTest extends AbstractUseCaseTest {

  public static final Path USECASE_DIR = getResourcesDirectory("graphql-validation");

  @SneakyThrows
  @ParameterizedTest
  @ArgumentsSource(UseCaseFiles.class)
  void testUsecase(Path script, Path graphQlFile, Path packageFile) {
    super.testUsecase(script, graphQlFile, packageFile);
  }

  static class UseCaseFiles extends SqrlScriptsAndLocalPackages {
    public UseCaseFiles() {
      super(USECASE_DIR, true);
    }
  }
}
