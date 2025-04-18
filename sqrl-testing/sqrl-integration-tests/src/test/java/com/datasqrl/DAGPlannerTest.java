package com.datasqrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datasqrl.cmd.AssertStatusHook;
import com.datasqrl.util.SnapshotTest.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class DAGPlannerTest extends AbstractAssetSnapshotTest {

  public static final Path SCRIPT_DIR = getResourcesDirectory("dagplanner");

  protected DAGPlannerTest() {
    super(SCRIPT_DIR.resolve("plan-output"));
  }

  @ParameterizedTest
  @ArgumentsSource(DagPlannerSQRLFiles.class)
  void testScripts(Path script) {
    assertTrue(Files.exists(script));
    TestNameModifier testModifier = TestNameModifier.of(script);
    boolean expectFailure = testModifier==TestNameModifier.fail;
    boolean printMessages = testModifier==TestNameModifier.fail || testModifier==TestNameModifier.warn;
    this.snapshot = Snapshot.of(getDisplayName(script), getClass());
    AssertStatusHook hook = execute(SCRIPT_DIR, "compile", script.getFileName().toString(), "-t", outputDir.getFileName().toString());
    assertEquals(expectFailure, hook.isFailed(), hook.getMessages());
    if (printMessages) {
      createMessageSnapshot(hook.getMessages());
    } else {
      createSnapshot();
    }
  }

  @Override
  public Predicate<Path> getBuildDirFilter() {
    return file -> {
      switch (file.getFileName().toString()) {
        case "pipeline_explain.txt": return true;
      }
      return false;
    };
  }

  public Predicate<Path> getOutputDirFilter() {
    return path -> {
      if (path.getFileName().toString().equals("flink-sql-no-functions.sql")) return true;
      if (path.getFileName().toString().contains("flink")) return false;
      if (path.getFileName().toString().contains("schema") || path.getFileName().toString().contains("views")) return false;
      return true;
    };
  }

  static class DagPlannerSQRLFiles extends SqrlScriptArgumentsProvider {
    public DagPlannerSQRLFiles() {
      super(SCRIPT_DIR, true);
    }
  }

}
