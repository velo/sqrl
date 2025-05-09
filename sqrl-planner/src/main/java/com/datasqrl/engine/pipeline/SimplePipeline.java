/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine.pipeline;

import com.datasqrl.config.EngineType;
import com.datasqrl.engine.ExecutionEngine;
import com.datasqrl.error.ErrorCollector;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * A simple pipeline that has a single stream, log, and server engine with support
 * for multiple databases.
 */
@Value
public class SimplePipeline implements ExecutionPipeline {

  List<ExecutionStage> stages;

  HashMultimap<ExecutionStage, ExecutionStage> upstream;
  HashMultimap<ExecutionStage, ExecutionStage> downstream;

//  private SimplePipeline(List<ExecutionStage> stages) {
//    this.stages = stages;
//    for (int i = 0; i < stages.size(); i++) {
//      ExecutionStage stage = stages.get(i);
//      for (int j = i; j < stages.size(); j++) {
//        downstream.put(stage,stages.get(j));
//      }
//      for (int j = i; j >= 0 ; j--) {
//        upstream.put(stage,stages.get(j));
//      }
//    }
//  }

  public static SimplePipeline of(Map<String, ExecutionEngine> engines, ErrorCollector errors) {
    HashMultimap<ExecutionStage, ExecutionStage> upstream = HashMultimap.create(), downstream = HashMultimap.create();

    List<EngineStage> stages = new ArrayList<>();
    //A simple pipeline expects a certain set of stages
    Optional<EngineStage> logStage = getSingleStage(EngineType.LOG, engines);
    Optional<EngineStage> streamStage = getSingleStage(EngineType.STREAMS, engines);
    errors.checkFatal(streamStage.isPresent(), "Need to configure an enabled stream engine");
    List<EngineStage> dbStages = getStage(EngineType.DATABASE, engines);
    //TODO: create two stages for each configured server: one for logStage and one for dbStages
    Optional<EngineStage> serverStage = getSingleStage(EngineType.SERVER, engines);
    List<EngineStage> exportStages = getStage(EngineType.EXPORT, engines);

    logStage.ifPresent(ls -> {
      stages.add(ls);
      streamStage.ifPresent(ss -> downstream.put(ls, ss));
      streamStage.ifPresent(ss -> upstream.put(ls, ss));
      serverStage.ifPresent(vs -> downstream.put(ls, vs));
    });
    streamStage.ifPresent(ss -> {
      stages.add(ss);
      logStage.ifPresent(ls -> upstream.put(ss, ls));
      logStage.ifPresent(ls -> downstream.put(ss, ls));
      dbStages.forEach(dbs -> downstream.put(ss, dbs));
    });
    for (EngineStage dbStage : dbStages) {
      stages.add(dbStage);
      streamStage.ifPresent(ss -> upstream.put(dbStage, ss));
      serverStage.ifPresent(vs -> downstream.put(dbStage, vs));
    }
    for (EngineStage exportStage : exportStages) {
      stages.add(exportStage);
      streamStage.ifPresent(ss -> downstream.put(exportStage, ss));
    }
    serverStage.ifPresent(vs -> {
      stages.add(vs);
      dbStages.forEach(dbs -> upstream.put(vs, dbs));
      logStage.ifPresent(ls -> upstream.put(vs, ls));
    });
    //Engines that support computation can have themselves as up/downstream
    for (EngineStage stage : stages) {
      if (stage.getEngine().getType().isCompute()) {
        upstream.put(stage, stage);
        downstream.put(stage, stage);
      }
    }


    return new SimplePipeline(stages.stream().map(ExecutionStage.class::cast).collect(Collectors.toUnmodifiableList()),
        upstream, downstream);
  }

  private static List<EngineStage> getStage(EngineType engineType,
      Map<String, ExecutionEngine> engines) {
    List<EngineStage> engineList = engines.entrySet().stream()
        .filter(e -> e.getValue().getType() == engineType)
        .map(e -> new EngineStage(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
    return engineList;
  }

  private static Optional<EngineStage> getSingleStage(EngineType engineType, Map<String, ExecutionEngine> engines) {
    List<EngineStage> engineList = getStage(engineType, engines);
    if (engineList.size()==1) return Optional.of(engineList.get(0));
    else if (engineList.isEmpty()) return Optional.empty();
    throw new IllegalArgumentException(String.format("Expected a single %s engine but found multiple: %s", engineType, engineList));
  }

  @Override
  public Set<ExecutionStage> getUpStreamFrom(ExecutionStage stage) {
    Preconditions.checkArgument(upstream.containsKey(stage),"Invalid stage: %s",stage);
    return upstream.get(stage);
  }

  @Override
  public Set<ExecutionStage> getDownStreamFrom(ExecutionStage stage) {
    Preconditions.checkArgument(downstream.containsKey(stage),"Invalid stage: %s",stage);
    return downstream.get(stage);
  }
}
