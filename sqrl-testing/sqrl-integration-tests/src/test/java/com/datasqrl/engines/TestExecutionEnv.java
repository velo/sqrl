package com.datasqrl.engines;

import static org.junit.jupiter.api.Assertions.fail;

import com.datasqrl.DatasqrlTest;
import com.datasqrl.FullUsecasesIT.UseCaseTestParameter;
import com.datasqrl.config.PackageJson;
import com.datasqrl.config.SqrlConstants;
import com.datasqrl.engines.TestEngine.DuckdbTestEngine;
import com.datasqrl.engines.TestEngine.FlinkTestEngine;
import com.datasqrl.engines.TestEngine.IcebergTestEngine;
import com.datasqrl.engines.TestEngine.KafkaTestEngine;
import com.datasqrl.engines.TestEngine.PostgresLogTestEngine;
import com.datasqrl.engines.TestEngine.PostgresTestEngine;
import com.datasqrl.engines.TestEngine.SnowflakeTestEngine;
import com.datasqrl.engines.TestEngine.TestEngineVisitor;
import com.datasqrl.engines.TestEngine.TestTestEngine;
import com.datasqrl.engines.TestEngine.VertxTestEngine;
import com.datasqrl.engines.TestExecutionEnv.TestEnvContext;
import com.datasqrl.graphql.JsonEnvVarDeserializer;
import com.datasqrl.util.ResultSetPrinter;
import com.datasqrl.util.SnapshotTest.Snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;

@AllArgsConstructor
public class TestExecutionEnv implements TestEngineVisitor<Void, TestEnvContext> {

  String goal;
  PackageJson packageJson;
  Path rootDir;
  Snapshot snapshot;

  @SneakyThrows
  @Override
  public Void visit(PostgresTestEngine engine, TestEnvContext context) {
    if (hasTestEngine()) { //tested by Test goal
      return null;
    }

    if (hasServerEngine()) { //Tested by graphql queries
      return null;
    }

    //Snapshot views
    Map postgresPlan = new ObjectMapper().readValue(rootDir.resolve("build/deploy/plan/postgres.json").toFile(),
        Map.class);
    List<Map<String, Object>> view = (List<Map<String, Object>>)postgresPlan.get("views");
    String url = context.env.get("JDBC_URL");
    String username = context.env.get("PGUSER");
    String password = context.env.get("PGPASSWORD");
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      for (Map statement : (List<Map>) postgresPlan.get("statements")) {
        if (statement.get("type").toString().equalsIgnoreCase("view")) {
          String viewName = (String)statement.get("name");
          ResultSet resultSet = conn.createStatement()
              .executeQuery(String.format("SELECT * FROM \"%s\"", viewName));
          String string = ResultSetPrinter.toString(resultSet, (c) -> true, (c) -> true);
          snapshot.addContent(string, viewName);
        }
      }
    }

    return null;
  }

  @Override
  public Void visit(PostgresLogTestEngine engine, TestEnvContext context) {
    return null;
  }


  @Override
  public Void visit(KafkaTestEngine engine, TestEnvContext context) {
    return null;
  }

  @Override
  public Void visit(IcebergTestEngine engine, TestEnvContext context) {
    return null;
  }

  @Override
  public Void visit(DuckdbTestEngine engine, TestEnvContext context) {
    if (hasTestEngine()) { //tested by Test goal
      return null;
    }

    if (hasServerEngine()) { //Tested by graphql queries
      return null;
    }

    return null;
  }

  @SneakyThrows
  @Override
  public Void visit(VertxTestEngine engine, TestEnvContext context) {
    //Go through and execute each query (if not test goal)
    if (hasTestEngine()) {
      return null;
    }

    if (context.getParam().getTestPath() != null) {
      Path testPath = context.rootDir.resolve(context.getParam().getTestPath());
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(testPath, "*.graphql")) {
        List<Path> paths = new ArrayList<>();
        directoryStream.forEach(paths::add);

        // Sort the paths by filename
        paths.sort(Comparator.comparing(p -> p.getFileName().toString()));

        for (Path path : paths) {
          String query = Files.readString(path);
          String s = executeQuery(query);
          snapshot.addContent(s, path.getFileName().toString());
        }
      }
    }

    return null;
  }

  @SneakyThrows
  private String executeQuery(String query) {
    HttpClient client = HttpClient.newHttpClient();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8888/graphql"))
        .header("Content-Type", "application/graphql")
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new RuntimeException("Failed to post GraphQL query: " + response.body());
    }

    return response.body();
  }
  @SneakyThrows
  @Override
  public Void visit(SnowflakeTestEngine engine, TestEnvContext context) {
    if (hasTestEngine()) { //tested by Test goal
      return null;
    }

    //Install the snowflake schema

    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addDeserializer(String.class, new JsonEnvVarDeserializer(context.getEnv()));
    mapper.registerModule(module);

    Path schema = context.getRootDir()
        .resolve("build/plan/iceberg.json");
    Map map = mapper.readValue(schema.toFile(), Map.class);
    Map<String, List<Map<String, String>>> snowflake = (Map<String, List<Map<String, String>>>) ((Map) map.get(
        "engines")).get("snowflake");

    String url = (String)mapper.readValue(String.format("{\"url\": \"%s\"}",
        packageJson.getEngines().getEngineConfig("snowflake").get()
            .toMap().get("url")), Map.class).get("url");

    try (Connection connection = DriverManager.getConnection(url)) {
      for (Map<String, String> ddls : snowflake.get("ddl")) {
        boolean execute = connection.createStatement().execute(ddls.get("sql"));
//        if (!execute) fail("Could not execute query:" + ddls.get("sql"));
      }
      for (Map<String, String> ddls : snowflake.get("views")) {
        boolean execute = connection.createStatement().execute(ddls.get("sql"));
//        if (!execute) fail("Could not execute query:" + ddls.get("sql"));
      }

      for (Map<String, String> ddls : snowflake.get("views")) {
        ResultSet execute = connection.createStatement()
            .executeQuery(String.format("SELECT * FROM %s", ddls.get("name")));
        String string = ResultSetPrinter.toString(execute, (c) -> true, (c) -> true);
        snapshot.addContent(string, ddls.get("name"));
      }
    }


    return null;
  }

  @Override
  public Void visit(FlinkTestEngine engine, TestEnvContext context) {
    //todo test for flink-only use cases

    return null;
  }

  @Override
  public Void visit(TestTestEngine engine, TestEnvContext context) {
    DatasqrlTest test = new DatasqrlTest(null,
        context.rootDir.resolve(SqrlConstants.BUILD_DIR_NAME).resolve(SqrlConstants.DEPLOY_DIR_NAME).resolve(SqrlConstants.PLAN_DIR),
        context.env);
    try {
      int run = test.run();
      if (run != 0) {
        fail("Test runner returned error code. Check above for failed snapshot tests (in red) or exceptions");
      }
    } catch (Exception e) {
      fail(e);
    }
    return null;
  }

  private boolean hasTestEngine() {
    return packageJson.getEnabledEngines().contains("test");
  }

  private boolean hasServerEngine() {
    return packageJson.getEnabledEngines().contains("vertx");
  }

  @Builder
  @Getter
  public static class TestEnvContext {
    Path rootDir;
    Map<String, String> env;
    UseCaseTestParameter param;
  }
}
