# Alibaba Java Coding Guidelines

## Table of Contents

* [Preface](#preface)
* [1. Programming Specification](#1-programming-specification)
   * [Naming Conventions](#naming-conventions)
   * [Constant Conventions](#constant-conventions)
   * [Formatting Style](#formatting-style)
   * [OOP Rules](#oop-rules)
   * [Collection](#collection)
   * [Concurrency](#concurrency)
   * [Flow Control Statements](#flow-control-statements)
   * [Code Comments](#code-comments)
   * [Other](#other)
* [2. Exception and Logs](#2-exception-and-logs)
   * [Exception](#exception)
   * [Logs](#logs)
* [3. MySQL Rules](#3-mysql-rules)
   * [Table Schema Rules](#table-schema-rules)
   * [Index Rules](#index-rules)
   * [SQL Rules](#sql-rules)
   * [ORM Rules](#orm-rules)
* [4. Project Specification](#4-project-specification)
   * [Application Layers](#application-layers)
   * [Library Specification](#library-specification)
   * [Server Specification](#server-specification)
* [5. Security Specification](#5-security-specification)

## Preface
Alibaba Java Coding Guidelines consolidates the best programming practices from Alibaba Group's technical teams. This document consists of five parts: Programming Specification, Exception and Logs, MySQL Specification, Project Specification and Security Specification.

Rules are classified into three levels: Mandatory, Recommended and Reference.

## 1. Programming Specification

### Naming Conventions
1. [Mandatory] Names should not start or end with an underline or a dollar sign.
   - Counter example: _name / __name / $Object / name_ / name$ / Object$
2. [Mandatory] Using Chinese, Pinyin, or Pinyin-English mixed spelling in naming is strictly prohibited.
   - Positive example: alibaba / taobao / youku / Hangzhou.
3. [Mandatory] Class names should be nouns in UpperCamelCase except domain models: DO, BO, DTO, VO, etc.
   - Positive example: MarcoPolo / UserDO / HtmlDTO
4. [Mandatory] Method names, parameter names, member variable names, and local variable names should be written in lowerCamelCase.
   - Positive example: localValue / getHttpMessage() / inputUserId
5. [Mandatory] Constant variable names should be written in upper characters separated by underscores.
   - Positive example: MAX_STOCK_COUNT
6. [Mandatory] Abstract class names must start with Abstract or Base. Exception class names must end with Exception. Test case names shall start with the class names to be tested and end with Test.
7. [Mandatory] Brackets are a part of an Array type. The definition could be: String[] args;
8. [Mandatory] Do not add 'is' as prefix while defining Boolean variable.
9. [Mandatory] A package should be named in lowercase characters. There should be only one English word after each dot. Package names are always in singular format.
10. [Mandatory] Uncommon abbreviations should be avoided.
11. [Recommended] The pattern name is recommended to be included in the class name if any design pattern is used.
    - Positive example: OrderFactory / LoginProxy / ResourceObserver
12. [Recommended] Do not add any modifier, including public, to methods in interface classes.
13. Interface and implementation naming:
    - [Mandatory] All Service and DAO classes must be interfaces. Implementation class names should end with Impl.
    - [Recommended] If the interface name is to indicate the ability of the interface, its name should be an adjective (e.g., Translatable).
14. [For Reference] An Enumeration class name should end with Enum. Members should be upper case with underscores.
15. [For Reference] Naming conventions for different layers:
    - Service/DAO: get (single), list (multiple), count (stats), save/insert, remove/delete, update.
    - Domain: *DO (table), *DTO (domain), *VO (website/display).

### Constant Conventions
1. [Mandatory] Magic values are forbidden.
2. [Mandatory] 'L' instead of 'l' for long/Long.
3. [Recommended] Constants should be grouped in functional classes (e.g., CacheConsts).
4. [Recommended] Constants should be shared at the appropriate layer (Library, Application, Sub-project, Package, Class).
5. [Recommended] Use Enums for fixed ranges.

### Formatting Style
1. [Mandatory] Rules for braces: No line break before opening brace. Line break after opening brace. Line break before closing brace.
2. [Mandatory] No space between '(' and following char, or ')' and preceding char.
3. [Mandatory] Space between keywords (if/for/while/switch) and parentheses.
4. [Mandatory] Space at both sides of operators (=, &&, +, -, ternary).
5. [Mandatory] 4 spaces indent. No tabs.
6. [Mandatory] Column limit 120 characters. Wrap properly with 4 spaces indent.
7. [Mandatory] Space after comma in method parameters.
8. [Mandatory] Charset UTF-8, Unix line breaks.
9. [Recommended] No need to align variables with multiple spaces.
10. [Recommended] Use a single blank line to separate logical sections.

### OOP Rules
1. [Mandatory] Static field/method should be referred to by class name.
2. [Mandatory] Overridden methods must be marked with @Override.
3. [Mandatory] Varargs recommended only if parameters are same type/semantics. Avoid Object type.
4. [Mandatory] Do not modify method signatures of existing interfaces. Use @Deprecated if needed.
5. [Mandatory] Do not use deprecated classes/methods.
6. [Mandatory] Use constant.equals(object) or Objects.equals to avoid NPE.
7. [Mandatory] Use equals() for wrapper classes comparison, not == (except -128 to 127 for Integer).
8. [Mandatory] Floating-point numbers comparison: use error range or BigDecimal.
9. [Mandatory] Use wrapper classes for POJO members and RPC return/arguments. Local variables should be primitive.
10. [Mandatory] Do not assign default values to POJO members.
11. [Mandatory] Maintain serialVersionUID for compatibility.
12. [Mandatory] No business logic in constructors; use init().
13. [Mandatory] Implement toString() in POJO classes.
14. [Recommended] Check index bounds after String.split().
15. [Recommended] Group homonymous methods together.
16. [Recommended] Order: public/protected -> private -> getter/setter.
17. [Recommended] Setter argument name same as field name. No logic in getter/setter.
18. [Recommended] Use StringBuilder in loops for concatenation.
19. [Recommended] Use final appropriately.
20. [Recommended] Be cautious with Object.clone() (shallow copy).
21. [Recommended] Restrict access levels (private > protected > public).

### Collection
1. [Mandatory] Override hashCode if equals is overridden.
2. [Mandatory] Do not modify collections returned by keySet()/values()/entrySet().
3. [Mandatory] Do not modify immutable objects from Collections.emptyList() etc.
4. [Mandatory] Do not cast ArrayList.subList() to ArrayList.
5. [Mandatory] Be careful with subList when original list size changes.
6. [Mandatory] Use toArray(T[] array) with correct size.
7. [Mandatory] Arrays.asList() returns fixed-size list; do not add/remove.
8. [Mandatory] PECS principle for generics: <? extends T> for producer (get), <? super T> for consumer (add).
9. [Mandatory] Use Iterator to remove elements in a loop, not foreach.
10. [Mandatory] Comparator must handle equality and be transitive/symmetric.
11. [Recommended] Set initial capacity for collections.
12. [Recommended] Use entrySet() to traverse KV maps.
13. [Recommended] Check null-safety for different Map/Set implementations (HashMap vs ConcurrentHashMap).

### Concurrency
1. [Mandatory] Ensure thread-safety for singletons and their methods.
2. [Mandatory] Assign meaningful names to threads/pools.
3. [Mandatory] Use thread pools (ThreadPoolExecutor), not explicit threads.
4. [Mandatory] Avoid Executors utility (OOM risk).
5. [Mandatory] SimpleDateFormat is not thread-safe; use ThreadLocal or DateTimeFormatter (JDK8).
6. [Mandatory] Always call remove() on ThreadLocal variables in finally block.
7. [Mandatory] Block lock > Method lock; Object lock > Class lock.
8. [Mandatory] Consistent locking sequence to avoid deadlock.
9. [Mandatory] Lock outside try block.
10. [Mandatory] Use locks or optimistic locking (version) for concurrent updates.
11. [Mandatory] Use ScheduledExecutorService, not Timer.
12. [Recommended] Ensure CountDownLatch.countDown() is called in finally.
13. [Recommended] Avoid sharing Random across threads; use ThreadLocalRandom.
14. [Recommended] Use volatile for double-checked locking objects.
15. [For Reference] volatile for visibility; AtomicLong/LongAdder for atomicity.

### Flow Control Statements
1. [Mandatory] Each switch case must end with break/return or a fall-through comment. Must have default.
2. [Mandatory] Always use braces for if/else/for/while.
3. [Recommended] Use guard statements to reduce nesting (max 3 levels).
4. [Recommended] Use boolean variables for complex conditions.
5. [Recommended] Avoid heavy operations in loops (obj creation, DB connection, try-catch).
6. [Recommended] Check input parameter size for batch operations.

### Code Comments
1. [Mandatory] Javadoc for classes, variables, and methods (/** ... */).
2. [Mandatory] Author and Date info in classes.
3. [Mandatory] Single line comments (//) above code; multi-line (/* */).
4. [Mandatory] Enum fields must have Javadoc.
5. [Recommended] Keep comments updated with code changes.
6. [For Reference] Use TODO (pending logic) and FIXME (broken logic) with author and time.

### Other
1. [Mandatory] Precompile Regex Patterns.
2. [Mandatory] Math.random() returns [0, 1); use Random.nextInt() for integers.
3. [Mandatory] System.currentTimeMillis() for current time.
4. [Recommended] Specify size for data structures.
5. [Recommended] Remove obsolete code/config.

## 2. Exception and Logs

### Exception
1. [Mandatory] Do not catch Runtime exceptions for JDK built-ins (NPE, IndexOut); use pre-checks.
2. [Mandatory] Do not use exceptions for control flow.
3. [Mandatory] Catch specific exceptions, not big chunks of code.
4. [Mandatory] Do not suppress exceptions.
5. [Mandatory] Handle rollback if exception occurs.
6. [Mandatory] Close resources in finally or use try-with-resources.
7. [Mandatory] No return in finally block.
8. [Recommended] Method can return null; caller must check.
9. [Recommended] Distinguish "throw exception" vs "return error code" (RPC).
10. [Recommended] Use custom exceptions (DAOException, ServiceException).

### Logs
1. [Mandatory] Use SLF4J facade.
2. [Mandatory] Retain logs for at least 15 days.
3. [Mandatory] Proper log naming: appName_logType_logName.log.
4. [Mandatory] Use placeholders or conditional checks for TRACE/DEBUG/INFO.
5. [Mandatory] Set additivity="false" for loggers to avoid redundancy.
6. [Mandatory] Include context and stack trace in error logs.
7. [Recommended] Avoid excessive logs in production.

## 3. MySQL Rules

### Table Schema Rules
1. [Mandatory] Boolean columns: is_xxx (unsigned tinyint, 1=True, 0=False).
2. [Mandatory] Table/Column names: lower case, underscores, no digits-only parts.
3. [Mandatory] No plural nouns for table names.
4. [Mandatory] No MySQL keywords.
5. [Mandatory] Prefixes: pk_ (primary), uk_ (unique), idx_ (index).
6. [Mandatory] Decimals: use decimal type, not float/double.
7. [Mandatory] Required columns: id (unsigned bigint, auto-increment), gmt_create, gmt_modified (DATE_TIME).
8. [Recommended] SINGLE table sharding > 5M rows or > 2GB.

### Index Rules
1. [Mandatory] Use unique index for business logic.
2. [Mandatory] JOIN max 3 tables. Similar types required.
3. [Mandatory] No LIKE '%...' or '%...%' for pagination.
4. [Recommended] Utilize index order for ORDER BY (avoid file_sort).
5. [Recommended] Use Covering Index.
6. [Recommended] Use late join or sub-query for deep pagination.

### SQL Rules
1. [Mandatory] Use COUNT(*) for row counts.
2. [Mandatory] ISNULL() to check NULL.
3. [Mandatory] No Foreign Keys or Cascades in DB; handle in application.
4. [Mandatory] No Stored Procedures.
5. [Recommended] Evaluate IN clause size (< 1000).

### ORM Rules
1. [Mandatory] Specify column names, no SELECT *.
2. [Mandatory] Boolean mapping: is_xxx (DB) <-> xxx (POJO).
3. [Mandatory] No resultClass; use resultMap.
4. [Mandatory] Use #{} not ${} to prevent SQL injection.
5. [Mandatory] No HashMap/HashTable for query results.
6. [Mandatory] Update gmt_modified on every update.

## 4. Project Specification

### Application Layers
- Open Interface: RPC/HTTP, security, flow control.
- View: Rendering (Velocity, JS, etc.).
- Web Layer: Access control, parameter validation.
- Service Layer: Business logic.
- Manager Layer: Third-party encapsulation, general Service logic, DAO composition.
- DAO Layer: Data access.

Domain Models:
- DO: Data Object (Table structure).
- DTO: Data Transfer Object (Service/Manager).
- BO: Business Object (Business logic).
- VO: View Object (Display).
- Query: Query request objects.

### Library Specification
- GAV: GroupID, ArtifactID, Version.
- Version: prime.secondary.revision (initial 1.0.0).
- No SNAPSHOT in online applications.
- Uniform version variables for library groups.

## 5. Security Specification
1. [Mandatory] Authorization for user-owned pages/functions.
2. [Mandatory] Data desensitization for sensitive info.
3. [Mandatory] Parameter validation to prevent SQL injection, XSS, CSRF, ReDoS.
4. [Mandatory] Filter form/AJAX submissions for CSRF.
5. [Mandatory] Anti-replay restrictions (verification codes, rate limits).
