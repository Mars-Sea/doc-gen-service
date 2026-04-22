---
name: verify
description: Run tests and verify the project builds correctly. Use before committing or when you want to validate changes.
---

Run the full test suite and ensure the project compiles:

```bash
mvn test
```

If tests pass, also verify the package builds cleanly:

```bash
mvn clean package -DskipTests
```

Report any failures and suggest fixes.
