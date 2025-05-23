package com.datasqrl.v2.tables;

import com.datasqrl.v2.parser.AccessModifier;
import lombok.Value;

/**
 * Defines the visibility of a {@link com.datasqrl.v2.tables.SqrlTableFunction}
 */
@Value
public class AccessVisibility {

  public static final AccessVisibility NONE = new AccessVisibility(AccessModifier.NONE, false, false, true);

  /**
   * The type of access: Query or Subscription; if access type is NONE it means
   * the function cannot be queried and is only used for planning.
   */
  AccessModifier access;
  /**
   * If the table has been annotated as a test in the SQRL file
   */
  boolean isTest;
  /**
   * Access only table functions are only queryable and not added
   * to the planner/catalog, i.e. they cannot be referenced in subsequent definitions in a SQRL script
   * Access only functions represent relationships or functions generated for queryable tables.
   */
  boolean isAccessOnly;
  /**
   * Whether this table (function) is visible to external consumers (i.e. as
   * an API call or view)
   */
  boolean isHidden;


  public boolean isEndpoint() {
    return (access==AccessModifier.QUERY || access==AccessModifier.SUBSCRIPTION);
  }

  public boolean isQueryable() {
    return isEndpoint() && !isHidden;
  }

}
