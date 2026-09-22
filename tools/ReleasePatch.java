import io.sigpipe.jbsdiff.Diff;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

/**
 * Вспомогательный инструмент сборки релиза: считает bsdiff-патч между двумя
 * APK и печатает SHA-256 файла. Не входит в само приложение — приложению
 * нужен только bspatch (применение), а он уже подключён как обычная
 * Gradle-зависимость в app/build.gradle.kts.
 *
 * Запускается из release.sh:
 *   java -cp jbsdiff.jar;commons-compress.jar;. ReleasePatch diff old.apk new.apk out.patch
 *   java -cp jbsdiff.jar;commons-compress.jar;. ReleasePatch sha256 file.apk
 */
public class ReleasePatch {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: ReleasePatch diff <old> <new> <out> | sha256 <file>");
            System.exit(1);
        }

        switch (args[0]) {
            case "diff": {
                byte[] oldBytes = Files.readAllBytes(Paths.get(args[1]));
                byte[] newBytes = Files.readAllBytes(Paths.get(args[2]));
                try (OutputStream out = Files.newOutputStream(Paths.get(args[3]))) {
                    Diff.diff(oldBytes, newBytes, out);
                }
                break;
            }
            case "sha256": {
                byte[] bytes = Files.readAllBytes(Paths.get(args[1]));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(bytes);
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                System.out.println(sb);
                break;
            }
            default:
                System.err.println("Unknown command: " + args[0]);
                System.exit(1);
        }
    }
}
