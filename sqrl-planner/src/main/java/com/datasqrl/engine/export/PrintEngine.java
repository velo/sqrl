package com.datasqrl.engine.export;

import com.datasqrl.calcite.SqrlFramework;
import com.datasqrl.config.ConnectorConf;
import com.datasqrl.config.ConnectorConf.Context;
import com.datasqrl.config.ConnectorFactoryFactory;
import com.datasqrl.config.EngineType;
import com.datasqrl.config.TableConfig;
import com.datasqrl.datatype.DataTypeMapping;
import com.datasqrl.engine.EngineFeature;
import com.datasqrl.engine.EnginePhysicalPlan;
import com.datasqrl.engine.database.EngineCreateTable;
import com.datasqrl.engine.pipeline.ExecutionPipeline;
import com.datasqrl.engine.pipeline.ExecutionStage;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.v2.analyzer.TableAnalysis;
import com.datasqrl.v2.dag.plan.MaterializationStagePlan;
import com.datasqrl.v2.tables.FlinkTableBuilder;
import com.datasqrl.plan.global.PhysicalDAGPlan.StagePlan;
import com.datasqrl.plan.global.PhysicalDAGPlan.StageSink;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.calcite.rel.type.RelDataType;

public class PrintEngine implements ExportEngine {

  private final ConnectorConf connectorConf;

  @Inject
  public PrintEngine(ConnectorFactoryFactory connectorFactory) {
    this.connectorConf = connectorFactory.getConfig(PrintEngineFactory.NAME);
  }

  @Override
  public EngineCreateTable createTable(ExecutionStage stage, String originalTableName,
      FlinkTableBuilder tableBuilder, RelDataType relDataType, Optional<TableAnalysis> tableAnalysis) {
    tableBuilder.setConnectorOptions(connectorConf.toMapWithSubstitution(
        Context.builder()
            .tableName(tableBuilder.getTableName())
            .origTableName(originalTableName)
            .build()));
    return EngineCreateTable.NONE;
  }

  @Override
  public DataTypeMapping getTypeMapping() {
    return DataTypeMapping.NONE;
  }

  @Override
  public boolean supports(EngineFeature capability) {
    return false;
  }

  @Override
  public TableConfig getSinkConfig(String sinkName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EnginePhysicalPlan plan(StagePlan plan, List<StageSink> inputs, ExecutionPipeline pipeline,
      List<StagePlan> stagePlans, SqrlFramework framework, ErrorCollector errorCollector) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return PrintEngineFactory.NAME;
  }

  @Override
  public EngineType getType() {
    return EngineType.EXPORT;
  }
}
