package com.datasqrl.datatype;

import com.datasqrl.engine.stream.flink.connector.CastFunction;
import com.datasqrl.function.SqrlCastFunction;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.calcite.rel.type.RelDataType;

/**
 * For mapping Flink types to and from database engine types
 */
@FunctionalInterface
public interface DataTypeMapping {

  enum Direction {TO_DATABASE, FROM_DATABASE}

  DataTypeMapping NONE = (type) -> Optional.empty();

  /**
   *
   * @param type The datatype to map
   * @return The {@link Mapper} for the given datatype or empty if no type mapping is needed.
   */
  Optional<Mapper> getMapper(RelDataType type);

  interface Mapper {

    default Optional<SqrlCastFunction> getEngineMapping(Direction direction) {
      switch (direction) {
        case TO_DATABASE: return Optional.of(toEngineMapping());
        case FROM_DATABASE: return fromEngineMapping();
        default: throw new UnsupportedOperationException("Unrecognized direction: " + direction);
      }
    }

    /**
     * @return The {@link CastFunction} that maps the {@link RelDataType} to a supported engine type
     */
    SqrlCastFunction toEngineMapping();

    /**
     * @return The {@link CastFunction} that maps back to a type an database engine internal type
     * that is identical or similar to the original {@link RelDataType}. Returns empty when no such mapping
     * is possible or needed.
     */
    Optional<SqrlCastFunction> fromEngineMapping();
  }

  @Value
  @AllArgsConstructor
  class SimpleMapper implements Mapper {

    SqrlCastFunction toEngineMapping;
    Optional<SqrlCastFunction> fromEngineMapping;

    public SimpleMapper(SqrlCastFunction toEngineMapping, SqrlCastFunction fromEngineMapping) {
      this(toEngineMapping, Optional.of(fromEngineMapping));
    }

    @Override
    public SqrlCastFunction toEngineMapping() {
      return toEngineMapping;
    }

    @Override
    public Optional<SqrlCastFunction> fromEngineMapping() {
      return fromEngineMapping;
    }

  }

}
