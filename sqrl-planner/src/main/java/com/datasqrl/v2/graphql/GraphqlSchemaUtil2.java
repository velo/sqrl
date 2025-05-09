/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.v2.graphql;

import static com.datasqrl.canonicalizer.Name.isSystemHidden;

import com.datasqrl.canonicalizer.Name;
import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.graphql.server.CustomScalars;
import com.datasqrl.json.FlinkJsonType;
import com.datasqrl.schema.Multiplicity;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.flink.table.planner.plan.schema.RawRelDataType;

@Slf4j
public class GraphqlSchemaUtil2 {

  public static GraphQLType wrapNullable(GraphQLType gqlType, RelDataType type) {
    if (!type.isNullable()) {
      return GraphQLNonNull.nonNull(gqlType);
    }
    return gqlType;
  }

  public static GraphQLType wrapMultiplicity(GraphQLType type, Multiplicity multiplicity) {
    switch (multiplicity) {
      case ZERO_ONE:
        return type;
      case ONE:
        return GraphQLNonNull.nonNull(type);
      case MANY:
      default:
        return GraphQLList.list(GraphQLNonNull.nonNull(type));
    }
  }

  public static boolean isValidGraphQLName(String name) {
    return !isSystemHidden(name) && Pattern.matches("[_A-Za-z][_0-9A-Za-z]*", name);
  }

  public static Optional<GraphQLInputType> getGraphQLInputType(RelDataType type, NamePath namePath, boolean extendedScalarTypes) {
    return getGraphQLType(GraphQLMetaType.INPUT, type, namePath, extendedScalarTypes)
        .map(f->(GraphQLInputType)f).map(inputType -> (GraphQLInputType) wrapNullable(inputType, type));
  }

  public static Optional<GraphQLOutputType> getGraphQLOutputType(RelDataType type, NamePath namePath, boolean extendedScalarTypes) {
    return getGraphQLType(GraphQLMetaType.OUTPUT, type, namePath, extendedScalarTypes)
        .map(t -> (GraphQLOutputType) wrapNullable(t, type));
  }

  public static Optional<GraphQLType> getGraphQLType(GraphQLMetaType metaType, RelDataType type, NamePath namePath, boolean extendedScalarTypes) {
    if (type.getSqlTypeName() == null) {
      return Optional.empty();
    }

    switch (type.getSqlTypeName()) {
      case OTHER:
        if (type instanceof RawRelDataType) {
          RawRelDataType rawRelDataType = (RawRelDataType) type;
          Class<?> originatingClass = rawRelDataType.getRawType().getOriginatingClass();
          if (originatingClass.isAssignableFrom(FlinkJsonType.class)) {
            return Optional.of(CustomScalars.JSON);
          }
        }

        return Optional.empty();
      case BOOLEAN:
        return Optional.of(Scalars.GraphQLBoolean);
      case TINYINT:
      case SMALLINT:
      case INTEGER:
        return Optional.of(Scalars.GraphQLInt);
      case BIGINT:
        if (extendedScalarTypes) {
          return Optional.of(CustomScalars.GRAPHQL_BIGINTEGER);
        }
      case DECIMAL:
      case FLOAT:
      case REAL:
      case DOUBLE:
        return Optional.of(Scalars.GraphQLFloat);
      case DATE:
        return Optional.of(CustomScalars.DATE);
      case TIME:
        return Optional.of(CustomScalars.TIME);
      case TIME_WITH_LOCAL_TIME_ZONE:
      case TIMESTAMP:
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        return Optional.of(CustomScalars.DATETIME);
      case INTERVAL_YEAR:
      case INTERVAL_YEAR_MONTH:
      case INTERVAL_MONTH:
      case INTERVAL_DAY:
      case INTERVAL_DAY_HOUR:
      case INTERVAL_DAY_MINUTE:
      case INTERVAL_DAY_SECOND:
      case INTERVAL_HOUR:
      case INTERVAL_HOUR_MINUTE:
      case INTERVAL_HOUR_SECOND:
      case INTERVAL_MINUTE:
      case INTERVAL_MINUTE_SECOND:
      case INTERVAL_SECOND:
      case CHAR:
      case VARCHAR:
        return Optional.of(Scalars.GraphQLString);
      // arity many, create a GraphQLList of the component type
      case ARRAY:
      case MULTISET:
        return getGraphQLType(metaType, type.getComponentType(), namePath, extendedScalarTypes).map(GraphQLList::list);
     // nested type, arity 1
      case STRUCTURED:
      case ROW:
        return createGraphQLStructuredType(metaType, type, namePath, extendedScalarTypes);
      case MAP:
        return Optional.of(CustomScalars.JSON);
      case BINARY:
      case VARBINARY:
      case NULL:
      case ANY:
      case SYMBOL:
      case DISTINCT:
      case CURSOR:
      case COLUMN_LIST:
      case DYNAMIC_STAR:
      case GEOMETRY:
      case SARG:
      default:
        return Optional.empty();
    }
  }

  private static Optional<GraphQLType> createGraphQLStructuredType(GraphQLMetaType metaType, RelDataType rowType,
      NamePath namePath, boolean extendedScalarTypes) {
    String typeName = uniquifyNameForPath(namePath, metaType.suffix);
    final BiConsumer<String, GraphQLType> fieldConsumer;
    final Supplier<GraphQLType> buildResult;
    if (metaType==GraphQLMetaType.OUTPUT) {
      final GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
      builder.name(typeName);
      fieldConsumer = (fieldName, fieldType) -> builder.field(
          GraphQLFieldDefinition.newFieldDefinition()
              .name(fieldName)
              .type((GraphQLOutputType) fieldType)
              .build()
      );
      buildResult = builder::build;
    } else {
      final GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject();
      builder.name(typeName);
      fieldConsumer = (fieldName, fieldType) -> builder.field(
          GraphQLInputObjectField.newInputObjectField()
              .name(fieldName)
              .type((GraphQLInputType) fieldType)
              .build()
      );
      buildResult = builder::build;
    }
    for (RelDataTypeField field : rowType.getFieldList()) {
      final NamePath fieldPath = namePath.concat(Name.system(field.getName()));
      if (fieldPath.getLast().isHidden()) continue;
      RelDataType columnType = field.getType();
      getGraphQLType(metaType, columnType, fieldPath, extendedScalarTypes)
          .map(fieldType -> (GraphQLInputType) wrapNullable(fieldType, columnType))// recursively traverse
          .ifPresent(fieldType -> fieldConsumer.accept(field.getName(), fieldType));
    }
    return Optional.of(buildResult.get());
  }

  @AllArgsConstructor
  public enum GraphQLMetaType {
    INPUT("Input"), OUTPUT("Output");

    private String suffix;
  }

  /**
   * Create a unique type name from a NamePath by concatenating the display names separated by underscore (_)
   * @param fullPath
   * @return
   */
  public static String uniquifyNameForPath(NamePath fullPath) {
    return fullPath.toString("_");
  }

  public static String uniquifyNameForPath(NamePath fullPath, String postfix) {
    return uniquifyNameForPath(fullPath).concat(postfix);
  }
}
