import java.net.URL;

public class FileManager {

    /**
     * Tool class to manage files.
     */

    public static URL getResourceURL(String pathToFile) {
        return FileManager.class.getClassLoader().getResource(pathToFile);
    }

    public static String getResourcePathAsString(String pathToFile) {
        return String.valueOf(FileManager.class.getClassLoader().getResource(pathToFile));
    }

}
