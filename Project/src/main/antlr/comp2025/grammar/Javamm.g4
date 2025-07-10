grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
PRIVATE : 'private' ;
RETURN : 'return' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
BOOLEAN : 'boolean' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;
STRING : 'String' ;
IF : 'if' ;
WHILE : 'while' ;
CONTINUE : 'continue' ;
DO : 'do' ;
CONST : 'const' ;
SUPER : 'super' ;
THIS : 'this' ;



INTEGER : [0-9]+ ;
STRING_LITERAL : '"' (~["\\] | '\\' .)* '"' ;
ID : [a-zA-Z_$] [a-zA-Z0-9_$]* ;
WS : [ \t\n\r\f]+ -> skip ;

LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

program
    : importDecl* classDecl+ EOF
    ;

importDecl
    : IMPORT name=ID ('.' name=ID)* ';'  #ImportStmt
    ;

classDecl
    : CLASS name=ID (EXTENDS sname=ID)? // class declaration with optional superclass
       '{'
        varDecl*
        methodDecl*
        '}'
    ;

varDecl
    : type name=ID ';'
    ;

type
    : name=(INT | BOOLEAN | STRING | ID) ('[' ']') # ArrayType
    | name=(INT | BOOLEAN | STRING | ID)           # PrimitiveType
    | name=VOID                                    # VoidType
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})?
      ( (STATIC {$isStatic=true;})? type name=ID '(' (param (',' param)*)? ')' | (STATIC {$isStatic=true;}) type name=MAIN '(' STRING '[' ']' ID ')' )
      '{' varDecl* stmt* '}'
    ;

param
    : type name=ID          # NormalParam
    | type isVarArg = '...' name=ID    # VargArgsParam
    ;


stmt
    : expr ';' #ExprStmt
    | expr '=' expr ';' #AssignStmt
    | RETURN expr ';' #ReturnStmt
    | '{' stmt* '}' #BlockStmt
    | 'if' '(' expr ')' stmt ('else' stmt)? #IfStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    ;

expr
    : '('expr')' #ExprExpr
    | op= '!' expr #NotExpr
    | expr '[' expr ']' #ArrayAccessExpr
    | expr '.' 'length' #ArrayLengthExpr
    | name='length' '(' ')' #MethodLength
    | expr '.' expr #MethodCall
    | expr (op= '*'  | op= '/') expr #BinaryExpr
    | expr (op= '+' | op= '-') expr #BinaryExpr
    | expr (op= '>' | op= '>=' | op= '<' | op= '<=') expr #Comparison
    | expr (op= '==' | op= '!=') expr #Equality
    | expr (op= '&&' | op= '||') expr #Logical
    | '[' expr (',' expr)* ']' #ArrayExpr
    | 'new' INT '[' expr (',' expr)*  ']' #NewArrayExpr
    | 'new' name=ID '(' exprList? ')' #NewClassExpr
    | name=ID '(' (expr (',' expr)*)? ')' #MethodRefExpr
    | name=ID #VarRefExpr
    | value=INTEGER #IntegerLiteral
    | value=STRING_LITERAL #StringLiteral
    | value='true' #BooleanLiteral
    | value='false' #BooleanLiteral
    | 'this' #ThisExpr
    ;


exprList
    : expr (',' expr)*
    ;