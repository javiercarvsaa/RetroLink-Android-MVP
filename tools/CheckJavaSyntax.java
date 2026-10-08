import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.util.*;

/** Parser-only gate: it intentionally does not claim Android type checking or runtime validation. */
public final class CheckJavaSyntax {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> inputs = files.getJavaFileObjectsFromStrings(Arrays.asList(args));
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics, Arrays.asList("--release", "17", "-proc:none"), null, inputs);
            int units = 0;
            for (Object ignored : task.parse()) units++;
            boolean error = false;
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err.println(diagnostic); error = true;
            }
            if (error) throw new IllegalStateException("Invalid Java syntax");
            System.out.println("Java syntax: " + units + " source units PASS (not Android compilation)");
        }
    }
}
