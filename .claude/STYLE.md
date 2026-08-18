# STYLE

We are a fresher team on an 8-week prototype. Code should be plain, obvious and short.
**Not sloppy. Not enterprise.** Somebody who joined last week has to read it and change it.

## Do not, unless explicitly asked

- **No interface for a single implementation.** The one exception is an `api/` package, which is a
  cross-team seam and exists precisely so another team can compile against it.
- **No new abstraction layer.** No generics, no reflection, no annotation processing, no AOP.
- **No custom exception hierarchy.** Use `common/error/ApiException` with an existing `ErrorCode`.
- **No new error envelope and no new pagination.** `common/error` and `common/page` already exist.
- **No builder for fewer than 5 fields.** No factory for one type. No strategy for two cases.
  No visitor. Ever.
- **No caching, async, retry or circuit breaker** until something is measurably slow. "Might be slow"
  is not measurably slow.
- **No `@Data`.** It generates `equals`, `hashCode` and a setter for everything, including fields
  that must never change.
- **No `@SneakyThrows`.** It hides the thing the next reader needs to see.
- **No field injection**, so no `@Autowired` on a field.
- **No new dependency in `pom.xml`** without asking. A dependency is forever.

## Do

- **Constructor injection** via `@RequiredArgsConstructor` on the class.
- **Records for DTOs.** Entities may use `@Getter` and, only where genuinely mutable, `@Setter`.
- **`@Slf4j` for logging.** Never log a key, a token, a password, a `wrappedBek` or a
  `masterWrappedBek`. If you are unsure whether a value is sensitive, do not log it.
- **One public class per file.** Package-private for anything not on a seam. If it does not need to
  be public, it is not public.
- **Reuse before writing.** Check `common/` first, then the module's own `service/`. Most things you
  are about to write already exist.
- **Validate at the edge** with `@Valid` on the controller, not by hand inside the service.

## Budgets, and what to do when you hit one

These are not hard limits. They are the point at which you **stop and say something**.

| Limit | At the limit |
|---|---|
| Method over 30 lines | Say so and ask. Do not silently split it into five private methods |
| Class over 150 lines | Say so and ask. Do not silently split it into five files |
| More than 3 constructor parameters | Say so and ask. It usually means the class does two jobs |
| More than 2 levels of nesting | Extract a guard clause and return early |

**Saying "this is getting long, here are two options" is the correct behaviour.** Quietly producing a
package of eight small classes is not, because nobody on this team can then find anything.

## Tests

- **One happy path and one failure path per service method.** That is the bar. Not 100% coverage.
- Name the test after the behaviour: `returnsNotFoundWhenItemIsArchived`, not `test3`.
- **If a test needs 20 lines of setup, the design is wrong.** Say so instead of writing the setup.
- Use Testcontainers for anything that touches Mongo. Do not mock a repository to test a query.

## Comments

- Comment **why**, never what. `// re-sort because Mongo $in returns index order` is useful.
  `// loop over items` is noise.
- No commented-out code. Git remembers it.
- No `TODO` without a name and a date, or it will still be there in week 8.

## Naming

- Say what it is. `EntitlementResolver`, not `EntitlementHelper` or `EntitlementUtil`.
- No `Manager`, `Helper`, `Util`, `Processor`, `Handler` unless the framework demands it. Those
  names mean the class has no single job.
- Booleans read as questions: `entitled`, `copyLimited`, `hasSearchIndex`.
