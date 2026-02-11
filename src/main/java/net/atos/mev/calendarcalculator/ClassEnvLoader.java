package net.atos.mev.calendarcalculator;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

public class ClassEnvLoader {

	public static Properties loadFromMasterFile(String masterFile) {
		Properties prop = loadPropertiesFromClasspath(masterFile);
		String configFile = prop.getProperty("configFile");
		if (configFile == null || configFile.isBlank()) {
			throw new IllegalStateException("Missing configFile in master file: " + masterFile);
		}
		return loadFromConfigFile(configFile);
	}

	public static Properties loadFromConfigFile(String configFile) {
		if (configFile == null || configFile.isBlank()) {
			throw new IllegalArgumentException("configFile cannot be null/blank");
		}
		return loadPropertiesFromClasspath(configFile);
	}

	private static Properties loadPropertiesFromClasspath(String path) {
		try {
			URL url = ClassLoader.getSystemResource(path);
			if (url == null) {
				throw new IllegalStateException("Classpath resource not found: " + path);
			}
			Properties prop = new Properties();
			prop.load(url.openStream());
			return prop;
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot load classpath resource: " + path, exception);
		}
	}

	public static void main(String[] args) {

	}

}
