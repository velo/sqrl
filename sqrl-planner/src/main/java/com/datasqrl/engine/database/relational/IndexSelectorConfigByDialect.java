/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine.database.relational;

import com.datasqrl.config.JdbcDialect;
import com.datasqrl.plan.global.IndexDefinition;
import com.datasqrl.plan.global.IndexSelectorConfig;
import com.datasqrl.function.IndexType;
import com.google.common.base.Preconditions;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.EnumSet;

import static com.datasqrl.function.IndexType.BTREE;
import static com.datasqrl.function.IndexType.HASH;
import static com.datasqrl.function.IndexType.PBTREE;
import static com.datasqrl.function.IndexType.TEXT;
import static com.datasqrl.function.IndexType.VECTOR_COSINE;
import static com.datasqrl.function.IndexType.VECTOR_EUCLID;

@Value
@Builder
public class IndexSelectorConfigByDialect implements IndexSelectorConfig {

  public static final double DEFAULT_COST_THRESHOLD = 0.95;
  public static final int MAX_INDEX_COLUMNS = 3;

  @Getter
  @Builder.Default
  double costImprovementThreshold = DEFAULT_COST_THRESHOLD;
  @NonNull
  JdbcDialect dialect;
  @Builder.Default
  int maxIndexColumns = MAX_INDEX_COLUMNS;

  @Override
  public boolean hasPrimaryKeyIndex() {
    switch (dialect) {
      case Postgres:
      case MySQL:
      case Oracle:
      case H2:
      case SQLite:
        return true;
      case Iceberg:
        return false;
      default:
        throw new IllegalStateException("Dialect not supported:" + dialect.name());
    }
  }

  @Override
  public int maxIndexes() {
    switch (dialect) {
      case Iceberg:
        return 1;
      default:
        return 8;
    }
  }

  @Override
  public int maxIndexColumnSets() {
    switch (dialect) {
      case Iceberg:
        return Integer.MAX_VALUE;
      default:
        return 50;
    }
  }

  @Override
  public EnumSet<IndexType> supportedIndexTypes() {
    switch (dialect) {
      case Postgres:
      case MySQL:
      case Oracle:
        return EnumSet.of(HASH, BTREE, TEXT, VECTOR_COSINE, VECTOR_EUCLID);
      case H2:
      case SQLite:
        return EnumSet.of(HASH, BTREE);
      case Iceberg:
        return EnumSet.of(PBTREE);
      default:
        throw new IllegalStateException("Dialect not supported:" + dialect.name());
    }
  }

  @Override
  public int maxIndexColumns(IndexType indexType) {
    switch (dialect) {
      case Postgres:
        switch (indexType) {
          case HASH:
            return 1;
          default:
            return maxIndexColumns;
        }
      case MySQL:
      case H2:
      case SQLite:
      case Iceberg:
        return maxIndexColumns;
      default:
        throw new IllegalStateException("Dialect not supported: " + dialect.name());
    }
  }

  @Override
  public double relativeIndexCost(IndexDefinition index) {
    switch (index.getType()) {
      case HASH:
        return 1;
      case BTREE:
        return 1.5 + 0.1 * index.getColumns().size();
      case PBTREE:
        Preconditions.checkArgument(index.getPartitionOffset() >= 0);
        return 1 + 1.0/(index.getPartitionOffset()+1) + 0.1 * index.getColumns().size();
      default:
        return 1;
    }
  }

  public static IndexSelectorConfigByDialect of(JdbcDialect dialect) {
    return IndexSelectorConfigByDialect.builder().dialect(dialect).build();
  }

}
