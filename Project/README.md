# Compiler Project

### Membros

| Name                                | Contribuição | 
|-------------------------------------|--------------| 
| Lucas Greco Do Espírito Santo Jorge | 33.3%        | 
| Sara da Silva Azevedo               | 33.3%        | 
| Maureen Ah-shú                      | 33.3%        | 

## CP1

## 3.1 The Java-- Language

- Complete the Java-- grammar in ANTLR format
    - Import declarations
    - Class declaration (structure, fields and methods)
    - Statements (assignments, if-else, while, etc.)
    - Expressions (binary expressions, literals, method calls, etc.)
- Setup node names for the AST (e.g. “binaryOp” instead of “expr” for binary expressions)
- Annotate nodes in the AST with relevant information (e.g. id, values, etc.)
- Used interfaces: JmmParser, JmmNode and JmmParserResult

### Notes:

All implemented. Changed the grammar in order to get the firsts tests working.

## 3.2 Symbol Table

- Imported classes
- Declared class
- Fields inside the declared class
- Methods inside the declared class
- Parameters and return type for each method
- Local variables for each method
- Include type in each symbol (e.g. a local variable “a” is of type X. Also, is “a” array?)
- Used interfaces: SymbolTable, AJmmVisitor (the latter is optional)
-

### Notes:

All implemented. Adjusted grammar and changed the JmmSymbolTableBuilder and TypeUtils to extract and store the symbols
correctly.

## 3.3 Semantic Analysis

### 3.3.1 Types and Declarations Verification

- Verify if identifiers used in the code have a corresponding declaration, either as a local variable,
  a method parameter, a field of the class or an imported class
- Operands of an operation must have types compatible with the operation (e.g. int + boolean
  is an error because + expects two integers.)
- Array cannot be used in arithmetic operations (e.g. array1 + array2 is an error)
- Array access is done over an array
- Array access index is an expression of type integer
- Type of the assignee must be compatible with the assigned (an_int = a_bool is an error)
- Expressions in conditions must return a boolean (if(2+3) is an error)
- “this” expression cannot be used in a static method
- “this” can be used as an “object” (e.g. A a; a = this; is correct if the declared class is A or
  the declared class extends A)
- A vararg type when used, must always be the type of the last parameter in a method declaration.
  Also, only one parameter can be vararg, but the method can have several parameters
- Variable declarations, field declarations and method returns cannot be vararg
- Array initializer (e.g., [1, 2, 3]) can be used in all places (i.e., expressions) that can accept
  an array of integers

### Notes:

All implemented.

### 3.3.2 Method Verification

- When calling methods of the class declared in the code, verify if the types of arguments of the
  call are compatible with the types in the method declaration
- If the calling method accepts varargs, it can accept both a variable number of arguments of
  the same type as an array, or directly an array
- In case the method does not exist, verify if the class extends an imported class and report an
  error if it does not.
    - If the class extends another class, assume the method exists in one of the super classes,
      and that is being correctly called
- When calling methods that belong to other classes other than the class declared in the code,
  verify if the classes are being imported.
    - As explained in Section 1.2, if a class is being imported, assume the types of the expression
      where it is used are correct. For instance, for the code bool a; a = M.foo();, if M is an
      imported class, then assume it has a method named foo without parameters that returns
      a boolean.

### Notes:

All implemented.

Files created based on UndeclaredVariable:

* UndeclaredMethod - ensure the method calls reference valid methods
* UndeclaredeClass - ensures that the referenced class exists or is a subclass of an imported class.
* IncompatibleTypes - ensure that the types operands and assignees are compatible across all the operations

## CP2

## OLLIR

- Basic class structure (including constructor <init>)
- Class fields
- Method structure
- Assignments
- Arithmetic operations (with correct precedence)
- Method invocation
- Conditional instructions (if and if-else statements)
- Loops (while statement)
- Instructions related to arrays and varargs
- Declarations (use of the “Array” type): parameters, fields, ...
- Array accesses (b = a[0])
- Array assignments (a[0] = b)
- Array references (e.g. foo(a), where a is an array)

### Notes:

All implemented.
Extra tests created - Arithmetic Precedence, Array Complex Assign, Array Param Test, Boolean Arithmetic, Complex If Else
and Var Args.

## Optimization

- Register allocation
- Constant propagation and constant folding

### Notes:

All implemented.

## CP3

- Basic class structure (including constructor <init>)
- Class fields
- Method structure (
- Assignments
- Arithmetic operations (with correct precedence)
- Method invocation
- Conditional instructions (if and if-else statements)
- Loops (while statement)
- Instructions related to arrays
    - Declarations (use of “Array” type): parameters, fields, ...
    - Array accesses (b = a[0])
    - Array assignments (a[0] = b)
    - Array reference (e.g. foo(a), where a is an array)
- Calculate .limit locals and .limit stack
- low cost instructions
    - iload_x, istore_x, astore_x,aload_x (e.g., instead of iload x)
    - iconst_0, bipush, sipush, ldc (load constants to the stack with the appropriate instruction)
    - use of iinc (replace i=i+1 with i++)
    - iflt, ifne, etc (compare against zero, instead of two values, e.g., if_icmplt)

### Notes:

All implemented.