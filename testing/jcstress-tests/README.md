## jcstress tests

Use this to perform any concurrency validation via jcstress. This is a little
difficult to integrate with the MAT project proper, since Tycho and Eclipse
Orbit repos have no knowledge of jcstress. As a workaround, this project allows
one to perform validation testing during development.

### Wire up a new test

1. Set up a new test in the src/main/... path.

2. As needed, provide a symlink to the code in the eclipse MAT plugin.

### Build and run

```bash
$ cd testing/jcstress-tests
$ mvn clean verify
$ java -jar target/jcstress.jar
### wait some time

### review results in results/
$ ls -la results/
```
