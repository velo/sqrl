package com.datasqrl.engine.server;

import static com.datasqrl.engine.EngineFeature.NO_CAPABILITIES;

import com.datasqrl.calcite.SqrlFramework;
import com.datasqrl.config.EngineType;
import com.datasqrl.engine.EnginePhysicalPlan;
import com.datasqrl.engine.ExecutionEngine;
import com.datasqrl.engine.pipeline.ExecutionPipeline;
import com.datasqrl.engine.pipeline.ExecutionStage;
import com.datasqrl.error.ErrorCollector;
import com.datasqrl.plan.global.PhysicalDAGPlan.ServerStagePlan;
import com.datasqrl.plan.global.PhysicalDAGPlan.StagePlan;
import com.datasqrl.plan.global.PhysicalDAGPlan.StageSink;
import com.datasqrl.v2.tables.SqrlTableFunction;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * A generic java server engine.
 */
@Slf4j
public abstract class GenericJavaServerEngine extends ExecutionEngine.Base implements ServerEngine {

  public GenericJavaServerEngine(String engineName) {
    super(engineName, EngineType.SERVER, NO_CAPABILITIES);
  }

  @Override
  public EnginePhysicalPlan plan(com.datasqrl.v2.dag.plan.ServerStagePlan serverPlan) {
    serverPlan.getFunctions().stream().filter(fct -> fct.getExecutableQuery()==null).forEach(fct -> {
      throw new IllegalStateException("Function has not been planned: " + fct);
    });
    return new ServerPhysicalPlan(serverPlan.getFunctions(), serverPlan.getMutations(), null);
  }

  @Override
  public EnginePhysicalPlan plan(StagePlan plan, List<StageSink> inputs,
      ExecutionPipeline pipeline, List<StagePlan> stagePlans, SqrlFramework framework, ErrorCollector errorCollector) {

    Preconditions.checkArgument(plan instanceof ServerStagePlan);
    Set<ExecutionStage> dbStages = pipeline.getStages().stream().filter(s -> s.getEngine().getType()== EngineType.DATABASE).collect(
        Collectors.toSet());
//    Preconditions.checkArgument(dbStages.size()==1, "Currently only support a single database stage in server");
//    ExecutionEngine engine = Iterables.getOnlyElement(dbStages).getEngine();
//    Preconditions.checkArgument(engine instanceof AbstractJDBCEngine, "Currently the server only supports JDBC databases");
    return new ServerPhysicalPlan(List.of(), List.of(), /*Will set later after queries are generated*/null);
  }
}
