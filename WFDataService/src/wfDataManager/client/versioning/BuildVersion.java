package wfDataManager.client.versioning;

// Adapted from https://stackoverflow.com/questions/37642837/gradle-make-build-version-available-to-java
/**
 * Class for getting the current build version
 * @author MatNova
 *
 */
public class BuildVersion {
    public static String getBuildVersion(){
        return BuildVersion.class.getPackage().getImplementationVersion();
    }
}