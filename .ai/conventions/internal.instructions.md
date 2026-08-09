## Summary

This file is a collection of instructions, code example, rules, and agreements within the development team.
The points listed here have the highest priority and must be followed by all AI agents.
If there is a conflict between these instructions and any other document, these instructions take precedence.

## General Principles

---

### 1. Use Immutable Collection Operations

Prefer immutable collection operations over imperative loops when transforming collections. Use functional operations like `flatMap`, `map`, `filter`, etc., instead of manually constructing mutable collections.

**Example:**

```kotlin
// ❌ Avoid: Mutable collection construction
val result = mutableListOf<String>()
for (item in items) {
    result.addAll(item.subItems)
}

// ✅ Prefer: Immutable flatMap operation
val result = items.flatMap { it.subItems }
```

---

### 2. Use "require" for Arguments, "check" for State Validation

Use `require` to validate function arguments and preconditions at the entry point of a function.
Use `check` to validate internal state or invariants during execution.

**Example:**

```kotlin
// ✅ Use "require" for argument validation
fun findItem(id: Int? = null, key: String? = null): Item? {
    require(id != null || key != null) {
        "One of id or key must be non-null"
    }
    // ...
}

// ✅ Use "check" for state validation
fun createItem(): Item.Single {
    val item = buildItem()
    check(item is Item.Single) {
        "The newly created item must be of variant Single"
    }
    return item
}
```

---

### 3. Avoid Inline Styles in React Basic Components

Do not use inline `style` attributes in basic/reusable React components. Inline styles make it difficult for application
code to override styles using CSS classes and are discouraged in general HTML/React development
(including the official React documentation).


**Example:**

```tsx
// ❌ Avoid: Inline styles in basic components
export const Panel = ({ children }: PanelProps) => {
  return (
    <div style={{ flexGrow: 1, overflowX: "hidden" }}>
      {children}
    </div>
  );
};

// ✅ Prefer: A CSS class the application code can override
import styles from "./Panel.module.css";

export const Panel = ({ children }: PanelProps) => {
  return <div className={styles.panel}>{children}</div>;
};
```

---

### 4. Apply CSS Classes to Top-Level Components, Use Selectors for Nested Elements

When styling React components with CSS modules, apply the CSS class to the top-level component and use 
CSS selectors (descendant selectors, attribute selectors, etc.) to style nested elements.
Avoid applying separate CSS classes to each nested element.

This approach provides better encapsulation, makes styles easier to override from application code, 
and keeps the component markup cleaner.

**Example:**

```tsx
// ❌ Avoid: Applying separate classes to nested elements
// Panel.module.css
// .panelContent { flex-grow: 1; }
// .panelFooter { margin-top: auto; }

export const Panel = ({ children }: PanelProps) => {
  return (
    <div>
      <div className={styles.panelContent}>{children}</div>
      <div className={styles.panelFooter}>Footer content</div>
    </div>
  );
};

// ✅ Prefer: Single class on top-level, CSS selectors for nested elements
// Panel.module.css
// .panel { /* base styles */ }
// .panel [data-panel-part="content"] { flex-grow: 1; }
// .panel [data-panel-part="footer"] { margin-top: auto; }

export const Panel = ({ children }: PanelProps) => {
  return (
    <div className={styles.panel}>
      <div data-panel-part="content">{children}</div>
      <div data-panel-part="footer">Footer content</div>
    </div>
  );
};
```

---

### 5. Match Active Navigation Paths by Comparing Path Segments

When determining if a navigation menu item should be highlighted as "active", do not use simple `startsWith`
checks on the pathname. This causes false positives where similar paths incorrectly match
(e.g., `/pick` matching `/pick_tasks`).

Instead, split both paths into segments at each `/` delimiter and compare the segments exactly.
This ensures that only paths that truly start with the same route segments are matched.

**Example:**

```tsx
// ❌ Avoid: Using startsWith for path matching
<Menu.Item selected={pathname.startsWith(item.linkTo)} linkTo={item.linkTo} />
// Problem: "/items_archive" would match "/items"

// ✅ Prefer: Compare path segments exactly
const isPathActive = (pathname: string, linkTo: string): boolean => {
  // filter(Boolean) drops empty strings from leading/trailing slashes
  const pathSegments = pathname.split("/").filter(Boolean);
  const linkSegments = linkTo.split("/").filter(Boolean);

  return linkSegments.length <= pathSegments.length
    && linkSegments.every((segment, index) => segment === pathSegments[index]);
};

<Menu.Item selected={isPathActive(pathname, item.linkTo)} linkTo={item.linkTo} />
// ✅ "/items/123" matches "/items" (true)
// ✅ "/items_archive" does NOT match "/items" (false)
```

---

### 6. Use createLogger Instead of console.log for Application Logging

Do not use `console.log`, `console.warn`, `console.error`, or other console methods for application logging. 
Console logs do not get persisted and cannot be submitted to AGW (Application Gateway) for debugging production issues.

Instead, use the `createLogger` function from `@react-commons/utils/logging.ts` to create a logger instance. This ensures logs are:
- Persisted to local storage
- Available for submission to AGW
- Properly formatted with timestamps and log levels
- Accessible via `retrieveLastLogs()` for debugging

**Example:**

```tsx
// ❌ Avoid: Using console methods for application logging
export const useValueFormatter = () => {
  return (value: string | undefined): string => {
    if (!value) {
      console.log("No value provided");
      return "";
    }

    try {
      // ...formatting logic...
    } catch (error) {
      console.error("Error formatting value:", error);
      return "";
    }
  };
};

// ✅ Prefer: Using createLogger for proper logging
import { createLogger } from "@react-commons/utils/logging.ts";

const logger = createLogger("formatting");

export const useValueFormatter = () => {
  return (value: string | undefined): string => {
    if (!value) {
      // No need to log info level for optional parameters
      return "";
    }

    try {
      // ...formatting logic...
    } catch (error) {
      logger.error("Error formatting value", { value, error });
      return "";
    }
  };
};

// Available log levels:
// logger.debug("message", { context })  // For debugging information
// logger.info("message", { context })   // For informational messages
// logger.warn("message", { context })   // For warnings
// logger.error("message", { context })  // For errors
```

---

### 7. Use `Path` Instead of `File` for File System Operations (JVM/Kotlin)

Never use `java.io.File` for file system operations in JVM/Kotlin code. `java.io.File` is a legacy API.
Use `java.nio.file.Path` instead, which is the modern, more capable replacement.

Leverage Kotlin's `kotlin.io.path` extension functions (`exists()`, `createDirectories()`, `writeText()`,
`readText()`, `absolutePathString()`, etc.) to keep code idiomatic.

Only convert to `File` (via `.toFile()`) when required by a third-party API that exclusively accepts `File`.

**Example:**

```kotlin
// ❌ Avoid: Using java.io.File
import java.io.File

val dir = File(directoryPath)
if (!dir.exists()) {
    dir.mkdirs()
}
val file = File(dir, "output.txt")
file.writeText(content, Charsets.UTF_8)
println(file.absolutePath)

// ✅ Prefer: Using java.nio.file.Path with kotlin.io.path extensions
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

val dir = Path.of(directoryPath)
if (!dir.exists()) {
    dir.createDirectories()
}
val file = dir.resolve("output.txt")
file.writeText(content, Charsets.UTF_8)
println(file.absolutePathString())

// ✅ Only convert to File when a third-party API requires it
someLibrary.process(file.toFile())
```

---

### 8. Use CTEs for Optional Join-Based Filters in jOOQ Queries

When a query supports optional filters that require joining to a related table (e.g., filtering a parent entity
by properties of its child rows), express each such filter as a **conditional CTE** rather than inlining a
subquery or adding a direct join to the child table.

**Why CTEs:**
- A CTE built with `DSL.selectDistinct` on the child table produces exactly one row per parent ID, avoiding
  the row-multiplication that a plain `JOIN` to the child table causes.
- Each optional filter becomes an independent, self-describing CTE that is only created when its input is
  non-empty (using `nullIfEmpty()?.let { ... }`).
- All active CTEs are collected with `listOfNotNull(...)` and passed to `dsl.executeQuery(with = ...)`.
  Each non-null CTE is then conditionally joined in the `join` block using `innerJoin`.

**Example:**

```kotlin
// ✅ Prefer: Conditional CTE for each optional join-based filter

// Parents that have at least one child in the requested categories.
// selectDistinct guarantees one row per parent ID.
val childCategoryCTE = query.categoryIds.nullIfEmpty()?.let { categoryIds ->
    DSL.name("matching_parents")
        .fields("parent_id")
        .`as`(
            DSL.selectDistinct(CHILDREN.PARENT_ID)
                .from(CHILDREN)
                .where(CHILDREN.CATEGORY_ID.`in`(categoryIds)),
        )
}

// CTEs are passed via listOfNotNull – inactive ones (null) are automatically excluded
return dsl.executeQuery(
    with = listOfNotNull(childCategoryCTE),
    query = query,
    table = PARENTS,
    join = {
        // Inner-joined only when the CTE was created (i.e. its filter input was non-empty)
        if (childCategoryCTE != null) {
            innerJoin(childCategoryCTE).on(
                PARENTS.ID.eq(childCategoryCTE.field("parent_id", Int::class.java)),
            )
        }
    },
    where = query.toCondition(),
    // ...
)

// ❌ Avoid: Joining the child table directly – this multiplies rows per parent
return dsl.executeQuery(
    query = query,
    table = PARENTS,
    join = {
        // A parent with 5 matching children would appear 5 times
        if (query.categoryIds.isNotEmpty()) {
            innerJoin(CHILDREN).on(
                PARENTS.ID.eq(CHILDREN.PARENT_ID)
                    .and(CHILDREN.CATEGORY_ID.`in`(query.categoryIds))
            )
        }
    },
    // ...
)
```

---

### 9. Prefer `useState` Over `useRef` for Component-Owned Values

Use `useState` instead of `useRef` when tracking a value that logically belongs to a component's
state. `useRef` should be reserved for values that genuinely must not trigger a re-render:
DOM node references, timer/interval IDs, or previous-render tracking.

A common anti-pattern is reaching for `useRef` to "avoid re-renders" for a value that is really
part of the component's state. This often masks a deeper structural problem:
**the component being tracked is defined inside a hook or another component**.

When a component is declared inside a hook body:
1. Calling a state setter triggers a re-render of the outer hook/component.
2. The inner component is re-declared, producing a new function reference each time.
3. React sees a different component type at the same position and **unmounts/remounts** it,
   resetting all internal state — including input focus.

With `useRef`, writing to `.current` does not trigger a re-render, so the inner component's
reference stays the same and React reconciles it normally — silently masking the structural problem.

**The correct fix** is to move inner component definitions outside the hook, each into its own file
(required by `react-refresh/only-export-components` in Vite projects). Once component references
are stable across renders, `useState` works correctly and the codebase is honest about what is
reactive state and what is a mutable side-channel.

**Example:**

```tsx
// ❌ Avoid: useRef masking a structural problem (component defined inside a hook)
export const useEditModal = () => {
  const hasContentRef = useRef(false); // writing .current won't re-render → hides the bug

  // Every render creates a NEW TextField function reference.
  // React unmounts/remounts it on every re-render, resetting internal state and focus.
  const TextField = ({ onContentChange }: TextFieldProps) => {
    return <input onChange={(e) => onContentChange(e.target.value !== "")} />;
  };

  return (
    <Modal>
      <TextField onContentChange={(hasContent) => { hasContentRef.current = hasContent; }} />
    </Modal>
  );
};

// ✅ Prefer: useState with components extracted to their own files
// --- TextField.tsx (separate file — required by react-refresh rule) ---
export const TextField = ({ onContentChange }: TextFieldProps) => {
  return <input onChange={(e) => onContentChange(e.target.value !== "")} />;
};

// --- EditModal.tsx ---
// TextField is now a stable module-level reference. Re-renders of EditModal
// no longer recreate it, so useState is safe and focus is preserved.
export const EditModal = () => {
  const [hasContent, setHasContent] = useState(false); // ✅ triggers re-render safely

  return (
    <Modal preventDismiss={() => hasContent}>
      <TextField onContentChange={setHasContent} />
    </Modal>
  );
};
```

---

### 10. Use `testMethod_Condition` Naming for Unit Tests

Name unit test methods using the pattern `test<MethodOrFeature>_<Condition>`, where:
- `<MethodOrFeature>` is the method or feature under test (PascalCase).
- `<Condition>` is the specific scenario or expected outcome (PascalCase).

Do **not** use sentence-style names such as `"register returns 409 when username already exists"`.
This convention keeps test names concise, scannable, and consistent across the codebase.

**Example:**

```kotlin
// ❌ Avoid: Sentence-style test names
@Test
fun `register returns 409 when username already exists`() { ... }

@Test
fun `login returns 401 when password is wrong`() { ... }

// ✅ Prefer: testMethod_Condition naming
@Test
fun testRegister_DuplicateUsername() { ... }

@Test
fun testLogin_WrongPassword() { ... }
```

---

### 11. Avoid Calling `setState` Synchronously Inside `useEffect`

Do not call a state setter directly in the body of a `useEffect`. Synchronous `setState` inside an effect
causes a second render immediately after the first commit, producing cascading renders that hurt
performance and is flagged by React's lint rules (and the React docs:
https://react.dev/learn/you-might-not-need-an-effect).

Effects are meant to **synchronize React state with external systems** (DOM, subscriptions, timers,
network), not to derive state from other state or reset state when a prop changes.

**Common alternatives:**
- **Reset state on prop change** — track the previous prop value with `useState` and compare during render.
  No effect needed; React handles the re-render in a single pass.
- **Derive state from props/state** — compute the value inline during render instead of mirroring it
  into state via an effect.
- **Reset a whole subtree** — pass a changing `key` to the child component so React unmounts and
  remounts it cleanly.
- **Keep the effect only for the external-system part** (focusing a DOM node, starting a timer,
  subscribing) and move the state reset out.

**Example:**

```tsx
// ❌ Avoid: setState inside useEffect to reset state when a prop changes
export function InputPanel({ itemId, autoFocus }: Props) {
  const [text, setText] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setText("");            // ⚠️ cascading render
    if (autoFocus) inputRef.current?.focus();
  }, [itemId, autoFocus]);

  // ...
}

// ✅ Prefer: reset state during render via prev-prop comparison;
// keep only the DOM-focus side effect in useEffect.
export function InputPanel({ itemId, autoFocus }: Props) {
  const [text, setText] = useState("");
  const [prevItemId, setPrevItemId] = useState(itemId);
  const inputRef = useRef<HTMLInputElement>(null);

  // Render-phase reset — React folds this into the current render, no extra commit.
  if (prevItemId !== itemId) {
    setPrevItemId(itemId);
    setText("");
  }

  // Effect now only synchronizes with an external system (the DOM).
  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [itemId, autoFocus]);

  // ...
}
```

---

### 12. Point 12 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 13. Point 13 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 14. Point 14 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 15. Point 15 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 16. Point 16 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 17. Point 17 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 18. Point 18 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 19. Point 19 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

### 20. Point 20 Placeholder

**Example:**

```kotlin
// TODO: Add example
```

---

````
