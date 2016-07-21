# Code Guidelines

We are using relatively exact code and documentation guidelines in order to ensure
keeping our code and documentation simple to understand and easily extensible. Please
make sure to format all code you might submit according to our guidelines and read
the information below carefully!

## Classes

```Java
// Copyright notice

// Alphabetical imports of non-JDK classes

// Alphabetical imports of JDK classes

// Static imports (use only if argueably more readable!)

/**
 * @author BlackyPaw
 * @version 1.0
 */
public class Test {

	// Nested Types

	// Constants (static final)

	// Static Fields

	// Static Methods

	// Fields

	// Constructors

	// Methods

}
```

Whenever you make changes to a class or create a new one you will have to make sure
to follow the above class layout. All blank lines are intentional.

1. Before you begin to declare your class you will need to insert a JavaDoc comment which includes at least
   an author tag that contains your username as well as a version tag which will always contain 1.0 unless
   you are specifcally advised to change this number. Whenever you make major changes to a class that was not
   created by you make sure to add your name to the authors list.
2. Notice the blank line right after the opening brace of the class and just before its closing brace.
3. If your class has got nested enumerations or similar you should put them at the top of the file
4. On top of all that follows you may declare constant values, i.e. values that are not subject to be changed
   at runtime (static final).
5. You may declare all of the class' static fields right after all constant values
6. If your class possesses any static methods you may add them below the class' static fields
7. Static Methods are followed by non-static fields
8. Make sure to put all your class' constructors above the class' methods
9. Methods are to be put at the bottom of the class

You may subdivide each of the layout groups introduced above into smaller logical subgroups, e.g.

```Java
// Networking
private final PlayerConnection connection;

// Physics
private Vector3 position;
private Vector3 velocity;

// ====================================== ACCESSORS ====================================== //
public long getId() {
	return this.id;
}

public EntityType getType() {
	return this.type;
}
```

Just put the name of the subgroup inside a small EOL comment right above the first member of your subgroup. Use
simple comments for field declarations but use the more massive comments like ACCESSORS for your methods as
method groups tend to be much longer and more confusing.

Also, please stick to the following order of accessors inside each of your subgroups:
- public
- protected
- package-internal
- private

## Methods

```Java
/**
 * The application's main entry point.
 *
 * @param args Any arguments passed to the application via CLI
 */
public static void main( String[] args ) {
	return;
}
```

Please make sure to adhere the following set of rules:
- Document at least all methods that may be used from outside your class (i.e. are non-private)
  - Exception: If your class is only to be used by an implementation and the method in question is just an accessor
    you need not document it - even though you are still advised to do so.
- End your JavaDoc explanations with a dot ('.').
- Do not end with a dot in param, return, throws or other tags!
- Do not "include" the parameter's name inside its explanation, i.e.
  ```Java
  /**
   * @param name of the entity - BAD!
   */

  /**
   * @param name The entity's name - GOOD!
   */
  ```
- Notice the whitespace inside the parameter list parentheses
- Opening braces are to be put on the same line as your declaration, closing braces are to be put onto a separate line
- Indent your method appropriately (see below)

## Whitespaces

- Put whitespace inside and before parentheses, e.g.
  ```Java
  if ( a == b ) {

  }

  for ( int i = 0; i < 10; ++i ) {

  }
  ```
- Indent with tab characters (= 4 spaces)
- Feel free to insert blank lines if they make your code more understandable but do not exaggerate their use

## Miscellaneous

In general you should not exceed 80 characters of JavaDoc per line. Rather use multiple lines than extremely long ones.

This is list is not complete by any means. Therefore you do not need to be upset if your pull request gets rejected
for code style issues. If - on the other hand - you are hinting another contributor about inappropriate code-style
make sure to include an exact description of what needs to be corrected.

Also, we do provide a checkstyle file and settings for IntelliJ in order to make it easier for you to get used to our
conventions. You may find the checkstyle.xml file inside the repository and the IDE specific settings below.

Thank you for taking the time to read this through, you are awesome!