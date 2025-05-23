package com.datasqrl.v2.parser;

import com.datasqrl.canonicalizer.NamePath;
import lombok.Value;

/**
 * Represents an EXPORT statement
 */
@Value
public class SqrlImportStatement implements SqrlDdlStatement {

  ParsedObject<NamePath> packageIdentifier;
  ParsedObject<NamePath> alias;
  SqrlComments comments;

}
