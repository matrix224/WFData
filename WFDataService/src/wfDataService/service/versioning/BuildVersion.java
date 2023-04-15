package wfDataService.service.versioning;

// Adapted from https://stackoverflow.com/questions/37642837/gradle-make-build-version-available-to-java
public class BuildVersion {
    public static String getBuildVersion(){
        return BuildVersion.class.getPackage().getImplementationVersion();
    }
}