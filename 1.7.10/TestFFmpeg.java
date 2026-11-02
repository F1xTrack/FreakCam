import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

public class TestFFmpeg {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\FFmpeg\\bin\\ffmpeg.exe", "-f", "dshow", "-list_options", "true", "-i", "video=HD WebCam 2MP");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        Set<String> resolutions = new HashSet<>();
        Pattern pattern = Pattern.compile("s=(\\d+x\\d+)");
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                resolutions.add(m.group(1));
            }
        }
        p.waitFor();
        System.out.println("Resolutions found: " + resolutions);
    }
}
