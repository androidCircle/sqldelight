package com.alecstrong.sqlite.android;

import com.alecstrong.sqlite.android.lang.SqliteLanguage;
import com.alecstrong.sqlite.android.model.Column;
import com.alecstrong.sqlite.android.model.ColumnConstraint;
import com.alecstrong.sqlite.android.model.SqlStmt;
import com.alecstrong.sqlite.android.model.Table;
import com.google.common.base.Joiner;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.antlr.intellij.adaptor.lexer.ElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.TokenElementType;

public class TableGenerator {
  private final List<String> RULES = Arrays.asList(SQLiteParser.ruleNames);
  private final List<String> TOKENS = Arrays.asList(SQLiteParser.tokenNames);

  Table<ASTNode> generateTable(PsiFile file) {
    if (file.getChildren().length == 0) return null;
    try {
      ASTNode parse = childrenForRules(file.getNode(), SQLiteParser.RULE_parse)[0];
      ASTNode sqlStatementList = childrenForRules(parse, SQLiteParser.RULE_sql_stmt_list)[0];
      ASTNode sqlStatement = childrenForRules(sqlStatementList, SQLiteParser.RULE_sql_stmt)[0];
      ASTNode createStatement =
          childrenForRules(sqlStatement, SQLiteParser.RULE_create_table_stmt)[0];

      // Create the table model.
      String packageName =
          getPackageName(childrenForRules(parse, SQLiteParser.RULE_package_stmt)[0]);
      String tableName =
          childrenForRules(createStatement, SQLiteParser.RULE_table_name)[0].getText();
      Table<ASTNode> table = new Table<>(packageName, tableName, createStatement,
          new File(file.getProject().getBasePath() + "/build/generated-src"));

      // Add all the columns to the table model.
      ASTNode[] columns = childrenForRules(createStatement, SQLiteParser.RULE_column_def);
      for (ASTNode column : columns) {
        table.addColumn(columnFor(column));
      }

      // Add all the sql statements with identifiers to the table model.
      ASTNode[] sqlStmts = childrenForRules(sqlStatementList, SQLiteParser.RULE_sql_stmt);
      for (ASTNode sqlStmtNode : sqlStmts) {
        SqlStmt<ASTNode> sqlStmt = sqlStmtFor(sqlStmtNode);
        if (sqlStmt != null) table.addSqlStmt(sqlStmt);
      }

      return table;
    } catch (ArrayIndexOutOfBoundsException e) {
      // Expected something but it wasnt there, just return null;
      return null;
    }
  }

  private Column<ASTNode> columnFor(ASTNode column) {
    String columnName = childrenForRules(column, SQLiteParser.RULE_column_name)[0].getText();
    ASTNode typeNode = childrenForRules(column, SQLiteParser.RULE_type_name)[0];
    Column.Type type = Column.Type.valueOf(
        childrenForRules(typeNode, SQLiteParser.RULE_sqlite_type_name)[0].getText());
    Column<ASTNode> result = new Column<>(columnName, type, column);

    ASTNode[] columnConstraints = childrenForRules(column, SQLiteParser.RULE_column_constraint);
    for (ASTNode columnConstraintNode : columnConstraints) {
      ColumnConstraint<ASTNode> columnConstraint = columnConstraintFor(columnConstraintNode);
      if (columnConstraint != null) result.columnConstraints.add(columnConstraint);
    }
    return result;
  }

  private ColumnConstraint<ASTNode> columnConstraintFor(ASTNode columnConstraint) {
    for (ASTNode child : columnConstraint.getChildren(null)) {
      IElementType elementType = child.getElementType();
      if (!(elementType instanceof TokenElementType)) continue;
      switch (((TokenElementType) elementType).getType()) {
        // TODO: Handle constraints.
      }
    }
    return null;
  }

  private SqlStmt<ASTNode> sqlStmtFor(ASTNode sqlStmt) {
    ASTNode[] children = childrenForTokens(sqlStmt, SQLiteParser.IDENTIFIER);
    if (children.length == 0) return null;
    String identifier = children[0].getText();
    String stmt = sqlStmt.getLastChildNode().getText();
    return new SqlStmt<>(identifier, stmt, sqlStmt);
  }

  private String getPackageName(ASTNode packageNode) {
    List<String> names = new ArrayList<>();
    for (ASTNode name : childrenForRules(packageNode, SQLiteParser.RULE_name)) {
      names.add(name.getText());
    }
    return Joiner.on('.').join(names);
  }

  private ASTNode[] childrenForRules(ASTNode node, int... rules) {
    return node.getChildren(
        ElementTypeFactory.createRuleSet(SqliteLanguage.INSTANCE, RULES, rules));
  }

  private ASTNode[] childrenForTokens(ASTNode node, int... tokens) {
    return node.getChildren(
        ElementTypeFactory.createTokenSet(SqliteLanguage.INSTANCE, TOKENS, tokens));
  }
}